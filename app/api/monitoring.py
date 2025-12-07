"""API endpoints для управления мониторингом"""

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.monitoring_service import monitoring_service

router = APIRouter()


class MonitoringControl(BaseModel):
    """Модель для управления мониторингом"""
    action: str  # start, stop


@router.post("/start")
async def start_monitoring():
    """Запуск мониторинга"""
    monitoring_service.start_monitoring()
    return {"message": "Мониторинг запущен", "status": "running"}


@router.post("/stop")
async def stop_monitoring():
    """Остановка мониторинга"""
    monitoring_service.stop_monitoring()
    return {"message": "Мониторинг остановлен", "status": "stopped"}


@router.get("/status")
async def get_monitoring_status():
    """Получение статуса мониторинга"""
    return {
        "is_monitoring": monitoring_service.is_monitoring,
        "pending_violations": len(monitoring_service.get_pending_violations()),
        "total_violations": len(monitoring_service.get_all_violations())
    }

