"""Сервис для мониторинга нарушений"""

import cv2
import time
import threading
from pathlib import Path
from typing import List, Optional, Dict
from datetime import datetime
import uuid
import numpy as np
import asyncio

from app.services.video_service import video_service
from app.services.detection_service import detection_service, Detection
from app.services.zone_service import zone_service
from app.core.config import config
from app.utils.logger import logger


class Violation:
    """Класс для представления нарушения"""
    def __init__(self, zone_id: str, zone_name: str, detection: Detection, image_path: str):
        self.id = str(uuid.uuid4())
        self.zone_id = zone_id
        self.zone_name = zone_name
        self.detection = detection
        self.image_path = image_path
        self.timestamp = datetime.now().isoformat()
        self.status = "pending"  # pending, confirmed, false_positive
        self.operator_response = None
        self.operator_id = None
        self.response_time = None
    
    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "zone_id": self.zone_id,
            "zone_name": self.zone_name,
            "detection": self.detection.to_dict(),
            "image_path": self.image_path,
            "timestamp": self.timestamp,
            "status": self.status,
            "operator_response": self.operator_response,
            "operator_id": self.operator_id,
            "response_time": self.response_time
        }


class MonitoringService:
    """Сервис для мониторинга нарушений"""
    
    def __init__(self):
        self.is_monitoring = False
        self.monitoring_thread: Optional[threading.Thread] = None
        self.lock = threading.Lock()
        
        # Дебаунсинг: храним последние нарушения по зонам
        self.last_violations: Dict[str, float] = {}  # zone_id -> timestamp
        self.debounce_time = 5.0  # секунд между срабатываниями для одной зоны
        
        # Очередь уведомлений
        self.violations_queue: List[Violation] = []
        self.violations_storage: Dict[str, Violation] = {}
        
        # Директория для сохранения изображений
        self.violations_path = Path(config.get_violations_path())
        self.violations_path.mkdir(parents=True, exist_ok=True)
        
        # FPS для детекции (чтобы не перегружать систему)
        self.detection_fps = config.get('video.detection_fps', 10)
        self.last_detection_time = 0
    
    def start_monitoring(self):
        """Запуск мониторинга"""
        with self.lock:
            if self.is_monitoring:
                logger.warning("Мониторинг уже запущен")
                return
            
            logger.info("Запуск мониторинга нарушений")
            self.is_monitoring = True
            self.monitoring_thread = threading.Thread(target=self._monitoring_loop, daemon=True)
            self.monitoring_thread.start()
            logger.info("Поток мониторинга запущен")
    
    def stop_monitoring(self):
        """Остановка мониторинга"""
        with self.lock:
            if not self.is_monitoring:
                logger.warning("Мониторинг уже остановлен")
                return
            logger.info("Остановка мониторинга нарушений")
            self.is_monitoring = False
        
        # Ждем завершения потока мониторинга
        if self.monitoring_thread and self.monitoring_thread.is_alive():
            logger.debug("Ожидание завершения потока мониторинга...")
            self.monitoring_thread.join(timeout=2.0)  # Максимум 2 секунды
            if self.monitoring_thread.is_alive():
                logger.warning("Поток мониторинга не завершился в течение 2 секунд")
            else:
                logger.debug("Поток мониторинга успешно завершен")
        
        logger.info("Мониторинг остановлен")
    
    def _monitoring_loop(self):
        """Основной цикл мониторинга"""
        logger.info("Цикл мониторинга начал работу")
        frame_interval = 1.0 / self.detection_fps
        logger.debug(f"Интервал между детекциями: {frame_interval} сек (FPS: {self.detection_fps})")
        
        # Проверяем флаг shutdown приложения
        try:
            from app.main import app_shutting_down
        except ImportError:
            app_shutting_down = False
        
        while self.is_monitoring:
            # Проверяем флаг shutdown приложения
            try:
                from app.main import app_shutting_down
                if app_shutting_down:
                    logger.info("Получен сигнал shutdown, завершение цикла мониторинга")
                    break
            except ImportError:
                pass
            
            if not self.is_monitoring:
                break
            try:
                current_time = time.time()
                
                # Контроль FPS
                if current_time - self.last_detection_time < frame_interval:
                    time.sleep(0.01)
                    continue
                
                self.last_detection_time = current_time
                
                # Получаем кадр
                frame = video_service.get_raw_frame()
                if frame is None:
                    time.sleep(0.1)
                    continue
                
                # Проверяем, что видео воспроизводится
                if not video_service.is_playing:
                    time.sleep(0.1)
                    continue
                
                # Детекция людей
                detections = detection_service.detect(frame)
                if not detections:
                    continue
                
                logger.debug(f"Обнаружено {len(detections)} детекций")
                
                # Проверка нарушений
                violations = zone_service.check_violation(detections)
                
                # Обработка нарушений с дебаунсингом
                for violation_data in violations:
                    zone_id = violation_data["zone_id"]
                    detection = violation_data["detection"]
                    
                    # Дебаунсинг: проверяем, не было ли недавно нарушения в этой зоне
                    last_time = self.last_violations.get(zone_id, 0)
                    if current_time - last_time < self.debounce_time:
                        logger.debug(f"Нарушение в зоне {zone_id} пропущено из-за дебаунсинга")
                        continue
                    
                    logger.warning(f"Обнаружено нарушение в зоне {violation_data['zone_name']} (ID: {zone_id})")
                    
                    # Создаем нарушение
                    violation = self._create_violation(
                        zone_id=zone_id,
                        zone_name=violation_data["zone_name"],
                        detection=detections[0],  # Берем первую детекцию
                        frame=frame
                    )
                    
                    if violation:
                        self.last_violations[zone_id] = current_time
                        self._add_violation(violation)
                        # Логируем нарушение
                        self._log_violation(violation)
                        # Отправляем уведомление асинхронно
                        self._send_notification_async(violation)
                        logger.info(f"Нарушение {violation.id} зарегистрировано и отправлено")
            except Exception as e:
                logger.error(f"Ошибка в цикле мониторинга: {e}", exc_info=True)
                time.sleep(1)  # Небольшая задержка при ошибке
        
        logger.info("Цикл мониторинга завершил работу")
    
    def _create_violation(self, zone_id: str, zone_name: str, detection: Detection, frame: np.ndarray) -> Optional[Violation]:
        """Создание нарушения с сохранением изображения"""
        logger.debug(f"Создание нарушения для зоны {zone_name} (ID: {zone_id})")
        try:
            # Сохраняем изображение
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            image_filename = f"violation_{timestamp}_{zone_id[:8]}.jpg"
            image_path = self.violations_path / image_filename
            
            logger.debug(f"Сохранение изображения нарушения: {image_path}")
            
            # Рисуем bounding box на изображении
            annotated_frame = frame.copy()
            x1, y1, x2, y2 = detection.bbox
            cv2.rectangle(annotated_frame, (x1, y1), (x2, y2), (0, 0, 255), 2)
            cv2.putText(annotated_frame, f"Person {detection.confidence:.2f}", 
                       (x1, y1 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 2)
            
            # Сохраняем изображение
            success = cv2.imwrite(str(image_path), annotated_frame)
            if not success:
                logger.error(f"Не удалось сохранить изображение нарушения: {image_path}")
                return None
            
            logger.debug(f"Изображение успешно сохранено: {image_path}")
            
            # Создаем объект нарушения
            violation = Violation(
                zone_id=zone_id,
                zone_name=zone_name,
                detection=detection,
                image_path=str(image_path)
            )
            
            logger.info(f"Нарушение создано: ID={violation.id}, зона={zone_name}, уверенность={detection.confidence:.2f}")
            return violation
        except Exception as e:
            logger.error(f"Ошибка при создании нарушения: {e}", exc_info=True)
            return None
    
    def _add_violation(self, violation: Violation):
        """Добавление нарушения в очередь и хранилище"""
        with self.lock:
            self.violations_queue.append(violation)
            self.violations_storage[violation.id] = violation
    
    def get_pending_violations(self) -> List[Violation]:
        """Получение нарушений, ожидающих обработки"""
        with self.lock:
            return [v for v in self.violations_queue if v.status == "pending"]
    
    def get_violation(self, violation_id: str) -> Optional[Violation]:
        """Получение нарушения по ID"""
        with self.lock:
            return self.violations_storage.get(violation_id)
    
    def get_all_violations(self) -> List[Violation]:
        """Получение всех нарушений"""
        with self.lock:
            return list(self.violations_storage.values())
    
    def update_violation_status(self, violation_id: str, status: str, operator_id: Optional[str] = None) -> bool:
        """Обновление статуса нарушения"""
        with self.lock:
            violation = self.violations_storage.get(violation_id)
            if violation is None:
                return False
            
            violation.status = status
            violation.operator_id = operator_id
            violation.response_time = datetime.now().isoformat()
            
            if status == "confirmed":
                violation.operator_response = True
            elif status == "false_positive":
                violation.operator_response = False
            
            # Обновляем в логах
            try:
                from app.services.logging_service import logging_service
                logging_service.update_violation_status(
                    violation_id=violation_id,
                    status=status,
                    operator_id=operator_id,
                    operator_response=violation.operator_response
                )
            except Exception as e:
                print(f"Ошибка при обновлении статуса в логах: {e}")
            
            return True
    
    def delete_violation(self, violation_id: str) -> bool:
        """Удаление нарушения"""
        with self.lock:
            if violation_id not in self.violations_storage:
                return False
            
            # Удаляем из хранилища
            del self.violations_storage[violation_id]
            
            # Удаляем из очереди
            self.violations_queue = [v for v in self.violations_queue if v.id != violation_id]
            
            logger.info(f"Нарушение {violation_id} удалено из хранилища")
            return True
    
    def set_debounce_time(self, seconds: float):
        """Установка времени дебаунсинга"""
        with self.lock:
            self.debounce_time = max(0.0, seconds)
    
    def _log_violation(self, violation: Violation):
        """Логирование нарушения"""
        try:
            from app.services.logging_service import logging_service
            logging_service.log_violation(violation.to_dict())
        except Exception as e:
            print(f"Ошибка при логировании нарушения: {e}")
    
    def _send_notification_async(self, violation: Violation):
        """Асинхронная отправка уведомления"""
        try:
            # Импортируем здесь, чтобы избежать циклических зависимостей
            from app.services.notification_service import notification_service
            
            # Пытаемся использовать event loop приложения
            app_loop = notification_service.app_event_loop
            if app_loop is not None and app_loop.is_running():
                # Используем run_coroutine_threadsafe для выполнения в event loop приложения
                try:
                    future = asyncio.run_coroutine_threadsafe(
                        notification_service.send_notification(violation),
                        app_loop
                    )
                    # Не ждем результата, просто запускаем
                    logger.debug(f"Уведомление о нарушении {violation.id} отправлено через event loop приложения")
                except Exception as e:
                    logger.error(f"Ошибка при отправке уведомления через event loop приложения: {e}", exc_info=True)
            else:
                # Fallback: создаем новый event loop в отдельном потоке
                def send_in_thread():
                    try:
                        loop = asyncio.new_event_loop()
                        asyncio.set_event_loop(loop)
                        loop.run_until_complete(notification_service.send_notification(violation))
                        loop.close()
                    except Exception as e:
                        logger.error(f"Ошибка в потоке отправки уведомления: {e}", exc_info=True)
                
                thread = threading.Thread(target=send_in_thread, daemon=True)
                thread.start()
                logger.debug(f"Уведомление о нарушении {violation.id} отправлено через отдельный поток")
        except Exception as e:
            logger.error(f"Ошибка при отправке уведомления: {e}", exc_info=True)


# Глобальный экземпляр сервиса
monitoring_service = MonitoringService()

