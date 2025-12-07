"""Сервис для детекции людей"""

import cv2
import numpy as np
from typing import List, Tuple, Optional, Dict
from enum import Enum
import threading
from app.utils.logger import logger


class DetectionModel(Enum):
    """Доступные модели детекции"""
    YOLO = "yolo"
    MEDIAPIPE = "mediapipe"


class Detection:
    """Класс для хранения результата детекции"""
    def __init__(self, bbox: Tuple[int, int, int, int], confidence: float, class_id: int = 0):
        self.bbox = bbox  # (x1, y1, x2, y2)
        self.confidence = confidence
        self.class_id = class_id
        self.center = ((bbox[0] + bbox[2]) // 2, (bbox[1] + bbox[3]) // 2)
    
    def to_dict(self) -> dict:
        return {
            "bbox": self.bbox,
            "confidence": float(self.confidence),
            "center": self.center,
            "class_id": self.class_id
        }


class DetectionService:
    """Сервис для детекции людей"""
    
    def __init__(self):
        self.current_model: Optional[DetectionModel] = None
        self.yolo_model = None
        self.mediapipe_pose = None
        self.lock = threading.Lock()
        self.confidence_threshold = 0.5
        self.iou_threshold = 0.45
    
    def _load_yolo(self):
        """Загрузка YOLO модели"""
        logger.info("Начало загрузки YOLO модели")
        try:
            logger.debug("Импорт ultralytics")
            from ultralytics import YOLO
            import torch
            
            model_path = 'yolov8n.pt'
            logger.info(f"Загрузка модели YOLO из {model_path}")
            logger.info("Это может занять некоторое время при первом запуске (скачивание модели)...")
            
            # Исправление для PyTorch 2.6+: добавляем безопасные глобалы для ultralytics
            try:
                from ultralytics.nn.tasks import DetectionModel
                # Добавляем DetectionModel в безопасные глобалы для torch.load
                if hasattr(torch.serialization, 'add_safe_globals'):
                    torch.serialization.add_safe_globals([DetectionModel])
                    logger.debug("Добавлен DetectionModel в безопасные глобалы для PyTorch 2.6+")
            except Exception as e:
                logger.warning(f"Не удалось настроить безопасные глобалы (может быть несовместимость версий): {e}")
            
            # Загружаем предобученную модель YOLOv8
            self.yolo_model = YOLO(model_path)  # nano версия для скорости
            
            logger.info("YOLO модель успешно загружена")
            return True
        except ImportError as e:
            logger.error(f"Не удалось импортировать ultralytics: {e}")
            logger.error("Убедитесь, что ultralytics установлен: pip install ultralytics")
            return False
        except Exception as e:
            error_msg = str(e)
            if "weights_only" in error_msg or "WeightsUnpickler" in error_msg:
                logger.error("Ошибка загрузки модели из-за изменений в PyTorch 2.6")
                logger.error("Попробуйте обновить ultralytics: pip install --upgrade ultralytics")
                logger.error("Или используйте MediaPipe модель вместо YOLO")
            else:
                logger.error(f"Ошибка при загрузке YOLO модели: {e}", exc_info=True)
                logger.error("Проверьте наличие файла модели или подключение к интернету для скачивания")
            return False
    
    def _load_mediapipe(self):
        """Загрузка MediaPipe модели"""
        logger.info("Начало загрузки MediaPipe модели")
        try:
            logger.debug("Импорт mediapipe")
            import mediapipe as mp
            
            logger.debug("Создание MediaPipe Pose")
            mp_pose = mp.solutions.pose
            self.mediapipe_pose = mp_pose.Pose(
                min_detection_confidence=0.5,
                min_tracking_confidence=0.5
            )
            
            logger.info("MediaPipe модель успешно загружена")
            return True
        except ImportError as e:
            logger.error(f"Не удалось импортировать mediapipe: {e}")
            logger.error("Убедитесь, что mediapipe установлен: pip install mediapipe")
            return False
        except Exception as e:
            logger.error(f"Ошибка при загрузке MediaPipe модели: {e}", exc_info=True)
            return False
    
    def set_model(self, model_name: str) -> bool:
        """Установка модели детекции"""
        logger.info(f"Попытка установки модели детекции: {model_name}")
        with self.lock:
            try:
                model = DetectionModel(model_name.lower())
                logger.debug(f"Модель распознана: {model.value}")
            except ValueError:
                logger.error(f"Неизвестная модель: {model_name}. Доступные модели: {[m.value for m in DetectionModel]}")
                return False
            
            # Освобождаем предыдущую модель
            if self.current_model == DetectionModel.MEDIAPIPE and self.mediapipe_pose:
                logger.debug("Освобождение предыдущей MediaPipe модели")
                try:
                    self.mediapipe_pose.close()
                except Exception as e:
                    logger.warning(f"Ошибка при закрытии MediaPipe: {e}")
                self.mediapipe_pose = None
            
            if self.current_model == DetectionModel.YOLO and self.yolo_model:
                logger.debug("Освобождение предыдущей YOLO модели")
                self.yolo_model = None
            
            # Загружаем новую модель
            logger.info(f"Загрузка модели {model.value}")
            if model == DetectionModel.YOLO:
                if not self._load_yolo():
                    logger.error(f"Не удалось загрузить модель YOLO")
                    return False
            elif model == DetectionModel.MEDIAPIPE:
                if not self._load_mediapipe():
                    logger.error(f"Не удалось загрузить модель MediaPipe")
                    return False
            
            self.current_model = model
            logger.info(f"Модель {model.value} успешно установлена")
            return True
    
    def detect_yolo(self, frame: np.ndarray) -> List[Detection]:
        """Детекция с помощью YOLO"""
        if self.yolo_model is None:
            return []
        
        results = self.yolo_model(frame, conf=self.confidence_threshold, iou=self.iou_threshold)
        detections = []
        
        for result in results:
            boxes = result.boxes
            for box in boxes:
                # Проверяем, что это человек (class 0 в COCO dataset)
                if int(box.cls) == 0:  # 0 = person в COCO
                    x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
                    confidence = float(box.conf[0].cpu().numpy())
                    
                    detections.append(Detection(
                        bbox=(int(x1), int(y1), int(x2), int(y2)),
                        confidence=confidence,
                        class_id=0
                    ))
        
        return detections
    
    def detect_mediapipe(self, frame: np.ndarray) -> List[Detection]:
        """Детекция с помощью MediaPipe"""
        if self.mediapipe_pose is None:
            return []
        
        # MediaPipe работает с RGB
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = self.mediapipe_pose.process(rgb_frame)
        
        detections = []
        
        if results.pose_landmarks:
            # Получаем bounding box из ключевых точек
            landmarks = results.pose_landmarks.landmark
            
            # Находим границы
            xs = [lm.x for lm in landmarks]
            ys = [lm.y for lm in landmarks]
            
            h, w = frame.shape[:2]
            x1 = int(min(xs) * w)
            y1 = int(min(ys) * h)
            x2 = int(max(xs) * w)
            y2 = int(max(ys) * h)
            
            # Добавляем отступы
            padding = 20
            x1 = max(0, x1 - padding)
            y1 = max(0, y1 - padding)
            x2 = min(w, x2 + padding)
            y2 = min(h, y2 + padding)
            
            # MediaPipe не дает confidence для всего человека, используем среднее
            visibility = [lm.visibility for lm in landmarks]
            avg_confidence = sum(visibility) / len(visibility) if visibility else 0.5
            
            if avg_confidence >= self.confidence_threshold:
                detections.append(Detection(
                    bbox=(x1, y1, x2, y2),
                    confidence=avg_confidence,
                    class_id=0
                ))
        
        return detections
    
    def detect(self, frame: np.ndarray) -> List[Detection]:
        """Детекция людей в кадре"""
        with self.lock:
            if self.current_model is None:
                return []
            
            if self.current_model == DetectionModel.YOLO:
                return self.detect_yolo(frame)
            elif self.current_model == DetectionModel.MEDIAPIPE:
                return self.detect_mediapipe(frame)
            
            return []
    
    def set_confidence_threshold(self, threshold: float):
        """Установка порога уверенности"""
        with self.lock:
            self.confidence_threshold = max(0.0, min(1.0, threshold))
    
    def set_iou_threshold(self, threshold: float):
        """Установка порога IoU для NMS"""
        with self.lock:
            self.iou_threshold = max(0.0, min(1.0, threshold))
    
    def get_model_info(self) -> dict:
        """Получение информации о текущей модели"""
        with self.lock:
            return {
                "current_model": self.current_model.value if self.current_model else None,
                "available_models": [m.value for m in DetectionModel],
                "confidence_threshold": self.confidence_threshold,
                "iou_threshold": self.iou_threshold
            }
    
    def cleanup(self):
        """Освобождение ресурсов"""
        logger.info("Очистка ресурсов детекции")
        with self.lock:
            if self.mediapipe_pose:
                try:
                    self.mediapipe_pose.close()
                    logger.debug("MediaPipe модель закрыта")
                except Exception as e:
                    logger.warning(f"Ошибка при закрытии MediaPipe: {e}")
                self.mediapipe_pose = None
            self.yolo_model = None
            self.current_model = None
            logger.info("Ресурсы детекции освобождены")


# Глобальный экземпляр сервиса
detection_service = DetectionService()

