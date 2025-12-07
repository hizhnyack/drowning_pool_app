"""Основное приложение FastAPI"""

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from pathlib import Path

from app.core.config import config
from app.api import status, clients, video, models, zones, violations, monitoring, notifications, logs
from app.services.detection_service import detection_service
from app.services.video_service import video_service
from app.services.monitoring_service import monitoring_service
from app.services.notification_service import notification_service
from app.utils.logger import logger

# Глобальный флаг для отслеживания состояния приложения
app_shutting_down = False

# Создание приложения
app = FastAPI(
    title="МИСКА РИС",
    description="Многофункциональная Интеллектуализироанная Система Контроля Акружения",
    version="1.0.0"
)

# Настройка CORS для работы в локальной сети
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # В локальной сети разрешаем все источники
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Подключение роутеров
app.include_router(status.router, prefix="/api", tags=["status"])
app.include_router(clients.router, prefix="/api", tags=["clients"])
app.include_router(video.router, prefix="/api/video", tags=["video"])
app.include_router(models.router, prefix="/api/models", tags=["models"])
app.include_router(zones.router, prefix="/api/zones", tags=["zones"])
app.include_router(violations.router, prefix="/api/violations", tags=["violations"])
app.include_router(monitoring.router, prefix="/api/monitoring", tags=["monitoring"])
app.include_router(notifications.router, prefix="/api/notifications", tags=["notifications"])
app.include_router(logs.router, prefix="/api/logs", tags=["logs"])

# Статические файлы для веб-интерфейса
static_path = Path(__file__).parent.parent / "static"
if static_path.exists():
    app.mount("/static", StaticFiles(directory=str(static_path)), name="static")

# Шаблоны
templates_path = Path(__file__).parent.parent / "templates"
templates = Jinja2Templates(directory=str(templates_path)) if templates_path.exists() else None

# Инициализация при старте приложения
@app.on_event("startup")
async def startup_event():
    """Инициализация сервисов при старте"""
    logger.info("Запуск приложения...")
    # Инициализация модели детекции из конфигурации
    model_name = config.get_detection_model()
    if model_name:
        logger.info(f"Попытка загрузки модели детекции из конфигурации: {model_name}")
        try:
            success = detection_service.set_model(model_name)
            if success:
                logger.info(f"Модель детекции {model_name} успешно загружена при старте")
            else:
                logger.warning(f"Не удалось загрузить модель {model_name} при старте")
        except Exception as e:
            logger.error(f"Ошибка при загрузке модели {model_name}: {e}", exc_info=True)
    else:
        logger.info("Модель детекции не указана в конфигурации")
    logger.info("Приложение готово к работе")


@app.on_event("shutdown")
async def shutdown_event():
    """Очистка ресурсов при остановке"""
    global app_shutting_down
    app_shutting_down = True
    
    logger.info("Остановка приложения...")
    
    # Останавливаем мониторинг (первым, так как он может использовать другие сервисы)
    try:
        logger.info("Остановка мониторинга...")
        monitoring_service.stop_monitoring()
        logger.info("Мониторинг остановлен")
    except Exception as e:
        logger.error(f"Ошибка при остановке мониторинга: {e}", exc_info=True)
    
    # Останавливаем видеосервис
    try:
        logger.info("Остановка видеосервиса...")
        video_service.stop()
        logger.info("Видеосервис остановлен")
    except Exception as e:
        logger.error(f"Ошибка при остановке видеосервиса: {e}", exc_info=True)
    
    # Закрываем все WebSocket соединения
    try:
        logger.info("Закрытие WebSocket соединений...")
        # Получаем список всех подключенных клиентов и отключаем их
        connected_clients = notification_service.get_connected_clients()
        for client_id in connected_clients:
            try:
                notification_service.disconnect(client_id)
            except Exception as e:
                logger.warning(f"Ошибка при отключении клиента {client_id}: {e}")
        logger.info(f"Закрыто {len(connected_clients)} WebSocket соединений")
    except Exception as e:
        logger.error(f"Ошибка при закрытии WebSocket соединений: {e}", exc_info=True)
    
    # Очищаем ресурсы детекции
    try:
        logger.info("Очистка ресурсов детекции...")
        detection_service.cleanup()
        logger.info("Ресурсы детекции очищены")
    except Exception as e:
        logger.error(f"Ошибка при очистке ресурсов детекции: {e}", exc_info=True)
    
    logger.info("Приложение остановлено")


# Корневой маршрут
@app.get("/")
async def root(request: Request):
    if templates:
        return templates.TemplateResponse("index.html", {"request": request})
    return {
        "message": "МИСКА РИС",
        "version": "1.0.0",
        "docs": "/docs"
    }


# Страница логов
@app.get("/logs")
async def logs_page(request: Request):
    if templates:
        return templates.TemplateResponse("logs.html", {"request": request})
    return {"message": "Страница логов"}

