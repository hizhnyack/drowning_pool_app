"""Сервис для работы с видеопотоком"""

import cv2
import threading
import time
from pathlib import Path
from typing import Optional, Tuple
from enum import Enum
import numpy as np
from app.utils.logger import logger


class VideoSource(Enum):
    """Тип источника видео"""
    CAMERA = "camera"
    FILE = "file"
    NONE = "none"


class VideoService:
    """Сервис для работы с видео"""
    
    def __init__(self, camera_index: int = 0, camera_path: Optional[str] = None):
        self.camera_index = camera_index
        self.camera_path = camera_path  # Путь к устройству (например, /dev/video0)
        self.current_source = VideoSource.NONE
        self.cap: Optional[cv2.VideoCapture] = None
        self.video_file_path: Optional[str] = None
        self.is_playing = False
        self.lock = threading.Lock()
        self.frame_rate = 30.0
        self.last_frame: Optional[np.ndarray] = None
        self.last_frame_time = 0
    
    def start_camera(self) -> bool:
        """Запуск камеры"""
        logger.info(f"Попытка запуска камеры с индексом {self.camera_index}")
        logger.info("ШАГ 1: Начало функции start_camera()")
        
        # Проверка доступности камеры на Linux
        logger.info("ШАГ 2: Проверка доступности камеры на Linux")
        import platform
        if platform.system() == 'Linux':
            try:
                import subprocess
                logger.info("ШАГ 2.1: Запуск v4l2-ctl --list-devices")
                result = subprocess.run(['v4l2-ctl', '--list-devices'], 
                                      capture_output=True, timeout=2, text=True)
                if result.returncode == 0:
                    logger.info(f"Доступные V4L2 устройства:\n{result.stdout}")
                else:
                    logger.warning("v4l2-ctl не доступен, пропускаем проверку устройств")
            except (subprocess.TimeoutExpired, FileNotFoundError, Exception) as e:
                logger.warning(f"Не удалось проверить V4L2 устройства: {e}")
        
        logger.info("ШАГ 3: Попытка получить lock")
        with self.lock:
            logger.info("ШАГ 4: Lock получен, остановка предыдущего источника видео")
            self._stop_internal()  # Используем внутренний метод, так как lock уже получен
            logger.info("ШАГ 5: Предыдущий источник остановлен")
            
            try:
                logger.info(f"ШАГ 6: Начало открытия камеры {self.camera_index}")
                logger.info("ВАЖНО: Браузер НЕ будет спрашивать разрешение, так как камера открывается на сервере, а не в браузере")
                
                # Пробуем открыть камеру напрямую с таймаутом через threading
                logger.info("ШАГ 7: Импорт threading и queue")
                import threading
                import queue
                
                logger.info("ШАГ 8: Создание очередей")
                result_queue = queue.Queue()
                exception_queue = queue.Queue()
                logger.info("ШАГ 9: Очереди созданы")
                
                def open_camera():
                    try:
                        logger.info("ПОТОК: Функция open_camera() запущена")
                        import platform
                        import os
                        
                        # Определяем, что использовать: путь или индекс
                        camera_input = self.camera_path if self.camera_path else self.camera_index
                        logger.info(f"ПОТОК: Попытка создать VideoCapture для камеры: {camera_input}")
                        
                        cap = None
                        if platform.system() == 'Linux':
                            logger.info("ПОТОК: Используется Linux, пробуем V4L2 backend")
                            
                            # Если указан путь, используем его напрямую
                            if self.camera_path:
                                if os.path.exists(self.camera_path):
                                    logger.debug(f"Поток: Используем путь к устройству: {self.camera_path}")
                                    try:
                                        cap = cv2.VideoCapture(self.camera_path, cv2.CAP_V4L2)
                                        logger.debug(f"Поток: VideoCapture (V4L2, путь) создан")
                                    except Exception as e:
                                        logger.warning(f"Поток: Не удалось использовать V4L2 с путем, пробуем без backend: {e}")
                                        cap = cv2.VideoCapture(self.camera_path)
                                else:
                                    logger.error(f"Поток: Устройство {self.camera_path} не существует")
                                    result_queue.put(None)
                                    return
                            else:
                                # Используем индекс, но пробуем разные варианты
                                logger.info(f"ПОТОК: Используем индекс камеры: {self.camera_index}")
                                try:
                                    logger.info("ПОТОК: Вызов cv2.VideoCapture() с индексом и V4L2...")
                                    cap = cv2.VideoCapture(self.camera_index, cv2.CAP_V4L2)
                                    logger.info(f"ПОТОК: VideoCapture (V4L2, индекс) создан успешно")
                                except Exception as e:
                                    logger.warning(f"Поток: Не удалось использовать V4L2 с индексом, пробуем без backend: {e}")
                                    cap = cv2.VideoCapture(self.camera_index)
                                
                                # Если не открылось, пробуем /dev/video0 напрямую
                                if cap is None or not cap.isOpened():
                                    logger.debug("Поток: Камера не открылась по индексу, пробуем /dev/video0")
                                    try:
                                        if cap:
                                            cap.release()
                                    except:
                                        pass
                                    if os.path.exists('/dev/video0'):
                                        try:
                                            logger.debug("Поток: Пробуем /dev/video0 с V4L2")
                                            cap = cv2.VideoCapture('/dev/video0', cv2.CAP_V4L2)
                                            logger.debug("Поток: VideoCapture создан для /dev/video0 с V4L2")
                                            if not cap.isOpened():
                                                logger.debug("Поток: /dev/video0 не открылся с V4L2, пробуем без backend")
                                                try:
                                                    cap.release()
                                                except:
                                                    pass
                                                cap = cv2.VideoCapture('/dev/video0')
                                                logger.debug("Поток: VideoCapture создан для /dev/video0 без backend")
                                        except Exception as e:
                                            logger.warning(f"Поток: Ошибка при открытии /dev/video0: {e}")
                                            cap = None
                                    else:
                                        logger.warning("Поток: /dev/video0 не существует")
                        else:
                            cap = cv2.VideoCapture(camera_input)
                            logger.debug(f"Поток: VideoCapture создан")
                        
                        if cap is None:
                            logger.error("Поток: VideoCapture вернул None")
                            result_queue.put(None)
                            return
                        
                        logger.debug(f"Поток: Проверка isOpened()")
                        is_opened = cap.isOpened()
                        logger.debug(f"Поток: isOpened() = {is_opened}")
                        
                        if is_opened:
                            logger.debug("Поток: Камера успешно открыта, отправка в очередь")
                            result_queue.put(cap)
                        else:
                            logger.warning("Поток: Камера не открылась (isOpened() = False)")
                            try:
                                cap.release()
                            except:
                                pass
                            result_queue.put(None)
                    except Exception as e:
                        logger.error(f"Поток: Ошибка при открытии камеры: {e}", exc_info=True)
                        exception_queue.put(e)
                
                logger.info("ШАГ 10: Запуск потока для открытия камеры")
                thread = threading.Thread(target=open_camera, daemon=True)
                logger.info("ШАГ 11: Запуск потока")
                thread.start()
                logger.info("ШАГ 12: Поток запущен, ожидание результата...")
                
                logger.info("ШАГ 13: Ожидание завершения потока (таймаут 5 секунд)")
                thread.join(timeout=5.0)  # Уменьшен таймаут до 5 секунд
                logger.info(f"ШАГ 14: Поток завершился или истек таймаут. Поток жив: {thread.is_alive()}")
                
                if thread.is_alive():
                    logger.error("Таймаут при открытии камеры (более 5 секунд)")
                    logger.error("Камера может быть занята другим приложением или недоступна")
                    logger.error("Возможные причины:")
                    logger.error("1. Камера используется другим приложением (Zoom, Skype, браузер и т.д.)")
                    logger.error("2. Недостаточно прав доступа к /dev/video0")
                    logger.error("3. Камера не поддерживается OpenCV")
                    logger.info("Попробуйте выполнить в терминале:")
                    logger.info("  ls -l /dev/video*  # Проверить права доступа")
                    logger.info("  lsof /dev/video0   # Проверить, какое приложение использует камеру")
                    logger.info("  sudo chmod 666 /dev/video0  # Дать права на доступ к камере")
                    self.cap = None
                    return False
                
                # Проверяем исключения
                if not exception_queue.empty():
                    exception = exception_queue.get()
                    logger.error(f"Ошибка при открытии камеры: {exception}")
                    raise exception
                
                # Получаем результат
                if result_queue.empty():
                    logger.error("Поток завершился, но результат не получен")
                    self.cap = None
                    return False
                
                self.cap = result_queue.get()
                
                if self.cap is None:
                    logger.error(f"Не удалось открыть камеру с индексом {self.camera_index}")
                    logger.info("Проверьте, что камера подключена и не используется другим приложением")
                    return False
                
                logger.info(f"Камера {self.camera_index} успешно открыта")
                
                # Установка параметров камеры
                logger.debug("Установка параметров камеры")
                width_set = self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
                height_set = self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
                fps_set = self.cap.set(cv2.CAP_PROP_FPS, 30)
                
                # Получаем реальные значения
                actual_width = self.cap.get(cv2.CAP_PROP_FRAME_WIDTH)
                actual_height = self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT)
                actual_fps = self.cap.get(cv2.CAP_PROP_FPS)
                
                logger.info(f"Параметры камеры: {actual_width}x{actual_height} @ {actual_fps} FPS")
                logger.debug(f"Установка параметров: width={width_set}, height={height_set}, fps={fps_set}")
                
                # Тестовое чтение кадра для проверки работоспособности
                logger.debug("Тестовое чтение кадра")
                ret, frame = self.cap.read()
                if not ret:
                    logger.error("Не удалось прочитать кадр с камеры")
                    self.cap.release()
                    self.cap = None
                    return False
                
                logger.info(f"Тестовый кадр успешно прочитан: {frame.shape}")
                
                self.current_source = VideoSource.CAMERA
                self.is_playing = True
                self.video_file_path = None
                logger.info("Камера успешно запущена и готова к работе")
                return True
            except Exception as e:
                logger.error(f"Ошибка при запуске камеры: {e}", exc_info=True)
                if self.cap is not None:
                    try:
                        self.cap.release()
                    except:
                        pass
                    self.cap = None
                return False
    
    def load_video_file(self, file_path: str) -> bool:
        """Загрузка видеофайла"""
        logger.info(f"Попытка загрузки видеофайла: {file_path}")
        with self.lock:
            logger.debug("Остановка предыдущего источника видео")
            self._stop_internal()  # Используем внутренний метод, так как lock уже получен
            
            path = Path(file_path)
            if not path.exists():
                logger.error(f"Видеофайл не найден: {file_path}")
                return False
            
            logger.debug(f"Файл существует, размер: {path.stat().st_size} байт")
            
            try:
                logger.debug(f"Создание VideoCapture для файла: {file_path}")
                self.cap = cv2.VideoCapture(str(path))
                
                if not self.cap.isOpened():
                    logger.error(f"Не удалось открыть видеофайл: {file_path}")
                    return False
                
                logger.info(f"Видеофайл успешно открыт: {file_path}")
                
                # Получение информации о файле
                self.frame_rate = self.cap.get(cv2.CAP_PROP_FPS) or 30.0
                frame_count = int(self.cap.get(cv2.CAP_PROP_FRAME_COUNT))
                width = int(self.cap.get(cv2.CAP_PROP_FRAME_WIDTH))
                height = int(self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
                
                logger.info(f"Параметры видео: {width}x{height}, {self.frame_rate} FPS, {frame_count} кадров")
                
                self.current_source = VideoSource.FILE
                self.video_file_path = str(path)
                self.is_playing = False  # Файл не воспроизводится автоматически
                logger.info("Видеофайл успешно загружен")
                return True
            except Exception as e:
                logger.error(f"Ошибка при загрузке видеофайла: {e}", exc_info=True)
                if self.cap is not None:
                    try:
                        self.cap.release()
                    except:
                        pass
                    self.cap = None
                return False
    
    def play(self) -> bool:
        """Запуск воспроизведения"""
        with self.lock:
            if self.current_source == VideoSource.NONE:
                return False
            
            self.is_playing = True
            return True
    
    def pause(self):
        """Пауза воспроизведения"""
        with self.lock:
            self.is_playing = False
    
    def _stop_internal(self):
        """Внутренний метод остановки без lock (вызывается когда lock уже получен)"""
        if self.cap is not None:
            logger.debug("Освобождение ресурсов видео")
            try:
                self.cap.release()
                logger.debug("VideoCapture успешно освобожден")
            except Exception as e:
                logger.warning(f"Ошибка при освобождении VideoCapture: {e}")
            self.cap = None
        
        self.current_source = VideoSource.NONE
        self.is_playing = False
        self.video_file_path = None
        self.last_frame = None
        logger.debug("Видео сервис остановлен")
    
    def stop(self):
        """Остановка и освобождение ресурсов"""
        with self.lock:
            self._stop_internal()
    
    def get_frame(self) -> Optional[Tuple[bytes, float]]:
        """Получение текущего кадра в формате JPEG"""
        # Проверяем флаг shutdown
        try:
            from app.main import app_shutting_down
            if app_shutting_down:
                return None
        except ImportError:
            pass
        
        with self.lock:
            if self.cap is None or not self.is_playing:
                return None
            
            try:
                ret, frame = self.cap.read()
                
                if not ret:
                    # Если файл закончился, перематываем
                    if self.current_source == VideoSource.FILE:
                        self.cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                        ret, frame = self.cap.read()
                        if not ret:
                            return None
                    else:
                        return None
                
                # Кодирование в JPEG
                ret, buffer = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 85])
                if not ret:
                    return None
                
                self.last_frame = frame
                self.last_frame_time = time.time()
                
                return (buffer.tobytes(), self.frame_rate)
            except Exception as e:
                logger.warning(f"Ошибка при чтении кадра: {e}")
                return None
    
    def get_raw_frame(self) -> Optional[np.ndarray]:
        """Получение сырого кадра (для обработки)"""
        with self.lock:
            if self.cap is None or not self.is_playing:
                return self.last_frame if self.last_frame is not None else None
            
            ret, frame = self.cap.read()
            
            if not ret:
                if self.current_source == VideoSource.FILE:
                    self.cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                    ret, frame = self.cap.read()
                    if not ret:
                        return self.last_frame
                else:
                    return self.last_frame
            
            self.last_frame = frame
            return frame
    
    def get_info(self) -> dict:
        """Получение информации о текущем источнике"""
        with self.lock:
            info = {
                "source": self.current_source.value,
                "is_playing": self.is_playing,
                "frame_rate": self.frame_rate
            }
            
            if self.cap is not None:
                info["width"] = int(self.cap.get(cv2.CAP_PROP_FRAME_WIDTH))
                info["height"] = int(self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
                
                if self.current_source == VideoSource.FILE:
                    total_frames = int(self.cap.get(cv2.CAP_PROP_FRAME_COUNT))
                    current_frame = int(self.cap.get(cv2.CAP_PROP_POS_FRAMES))
                    info["total_frames"] = total_frames
                    info["current_frame"] = current_frame
                    info["progress"] = current_frame / total_frames if total_frames > 0 else 0
                    info["file_path"] = self.video_file_path
            
            return info


# Глобальный экземпляр сервиса
video_service = VideoService()

