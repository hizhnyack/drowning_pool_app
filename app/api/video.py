"""API endpoints для работы с видео"""

from fastapi import APIRouter, HTTPException, UploadFile, File
from fastapi import Request
from fastapi.responses import StreamingResponse
from typing import Optional
from pathlib import Path
import io
import time

from app.services.video_service import video_service, VideoSource
from app.core.config import config
from app.utils.logger import logger

router = APIRouter()


@router.get("/stream")
async def video_stream(request: Request):
    """Получение видеопотока в формате MJPEG"""
    
    def generate():
        """Генератор кадров для MJPEG потока"""
        try:
            from app.main import app_shutting_down
        except ImportError:
            app_shutting_down = False
        
        frame_count = 0
        max_iterations_without_frame = 100  # Максимум итераций без кадра
        
        while True:
            # Частая проверка флага shutdown
            try:
                from app.main import app_shutting_down
                if app_shutting_down:
                    logger.debug("Видеопоток завершен из-за shutdown")
                    break
            except ImportError:
                pass
            
            # Проверка отключения клиента
            if request.client is None:
                logger.debug("Клиент отключен, завершение видеопотока")
                break
            
            try:
                result = video_service.get_frame()
                if result is None:
                    max_iterations_without_frame -= 1
                    if max_iterations_without_frame <= 0:
                        logger.debug("Превышено количество итераций без кадра, завершение потока")
                        break
                    # Если кадр недоступен, проверяем shutdown
                    try:
                        from app.main import app_shutting_down
                        if app_shutting_down:
                            break
                    except ImportError:
                        pass
                    time.sleep(0.05)  # Уменьшена задержка для более быстрой реакции
                    continue
                
                max_iterations_without_frame = 100  # Сброс счетчика
                frame_bytes, fps = result
                
                # Формируем MJPEG кадр
                yield (b'--frame\r\n'
                       b'Content-Type: image/jpeg\r\n\r\n' + frame_bytes + b'\r\n')
                
                frame_count += 1
                
                # Контроль FPS (с проверкой shutdown)
                sleep_time = 1.0 / fps
                elapsed = 0
                while elapsed < sleep_time:
                    try:
                        from app.main import app_shutting_down
                        if app_shutting_down:
                            return
                    except ImportError:
                        pass
                    time.sleep(min(0.05, sleep_time - elapsed))
                    elapsed += 0.05
                    
            except GeneratorExit:
                logger.debug("Видеопоток прерван (клиент отключился)")
                break
            except Exception as e:
                logger.debug(f"Ошибка в видеопотоке: {e}")
                try:
                    from app.main import app_shutting_down
                    if app_shutting_down:
                        break
                except ImportError:
                    pass
                time.sleep(0.05)
        
        logger.debug(f"Видеопоток завершен. Отправлено кадров: {frame_count}")
    
    return StreamingResponse(
        generate(),
        media_type="multipart/x-mixed-replace; boundary=frame"
    )


@router.post("/upload")
async def upload_video(file: UploadFile = File(...)):
    """Загрузка видеофайла для тестирования"""
    # Проверка типа файла
    if not file.content_type or not file.content_type.startswith('video/'):
        raise HTTPException(status_code=400, detail="Файл должен быть видео")
    
    # Сохранение файла
    uploads_path = Path(config.get_uploads_path())
    uploads_path.mkdir(parents=True, exist_ok=True)
    
    file_path = uploads_path / file.filename
    
    try:
        with open(file_path, "wb") as f:
            content = await file.read()
            f.write(content)
        
        # Загрузка в сервис
        if video_service.load_video_file(str(file_path)):
            return {
                "message": "Видеофайл успешно загружен",
                "file_path": str(file_path),
                "info": video_service.get_info()
            }
        else:
            raise HTTPException(status_code=400, detail="Не удалось загрузить видеофайл")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Ошибка при загрузке файла: {str(e)}")


@router.post("/control")
async def control_video(action: str):
    """Управление воспроизведением (play/pause/stop)"""
    logger.info(f"Запрос на управление видео: action={action}")
    
    if action == "play":
        logger.debug("Обработка действия 'play'")
        if video_service.current_source == VideoSource.NONE:
            # Если источник не выбран, запускаем камеру
            logger.info("Источник не выбран, запускаем камеру")
            if not video_service.start_camera():
                logger.error("Не удалось запустить камеру")
                raise HTTPException(status_code=400, detail="Не удалось запустить камеру")
        else:
            logger.debug(f"Запуск воспроизведения из источника: {video_service.current_source}")
            if not video_service.play():
                logger.error("Не удалось запустить воспроизведение")
                raise HTTPException(status_code=400, detail="Не удалось запустить воспроизведение")
        logger.info("Воспроизведение успешно запущено")
        return {"message": "Воспроизведение запущено", "info": video_service.get_info()}
    
    elif action == "pause":
        logger.info("Приостановка воспроизведения")
        video_service.pause()
        return {"message": "Воспроизведение приостановлено", "info": video_service.get_info()}
    
    elif action == "stop":
        logger.info("Остановка воспроизведения")
        video_service.stop()
        return {"message": "Воспроизведение остановлено", "info": video_service.get_info()}
    
    elif action == "start_camera":
        logger.info("Запрос на запуск камеры")
        if not video_service.start_camera():
            logger.error("Не удалось запустить камеру через API")
            raise HTTPException(status_code=400, detail="Не удалось запустить камеру")
        logger.info("Камера успешно запущена через API")
        return {"message": "Камера запущена", "info": video_service.get_info()}
    
    else:
        logger.warning(f"Неизвестное действие: {action}")
        raise HTTPException(status_code=400, detail="Неизвестное действие. Используйте: play, pause, stop, start_camera")


@router.get("/info")
async def get_video_info():
    """Получение информации о текущем видеопотоке"""
    return video_service.get_info()

