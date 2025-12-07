"""API endpoints для статуса сервера"""

from fastapi import APIRouter
from datetime import datetime

router = APIRouter()


@router.get("/status")
async def get_status():
    """Получение статуса сервера"""
    return {
        "status": "running",
        "timestamp": datetime.now().isoformat(),
        "version": "1.0.0"
    }

