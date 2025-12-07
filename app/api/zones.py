"""API endpoints для управления запретными зонами"""

from fastapi import APIRouter, HTTPException
from typing import List

from app.models.zone import Zone, ZoneCreate, ZoneUpdate
from app.services.zone_service import zone_service

router = APIRouter()


@router.get("/", response_model=List[Zone])
async def get_zones():
    """Получение списка всех зон"""
    return zone_service.get_all_zones()


@router.post("/", response_model=Zone, status_code=201)
async def create_zone(zone_data: ZoneCreate):
    """Создание новой зоны"""
    try:
        return zone_service.create_zone(zone_data)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/{zone_id}", response_model=Zone)
async def get_zone(zone_id: str):
    """Получение зоны по ID"""
    zone = zone_service.get_zone(zone_id)
    if zone is None:
        raise HTTPException(status_code=404, detail="Зона не найдена")
    return zone


@router.put("/{zone_id}", response_model=Zone)
async def update_zone(zone_id: str, zone_data: ZoneUpdate):
    """Обновление зоны"""
    zone = zone_service.update_zone(zone_id, zone_data)
    if zone is None:
        raise HTTPException(status_code=404, detail="Зона не найдена")
    return zone


@router.delete("/{zone_id}")
async def delete_zone(zone_id: str):
    """Удаление зоны"""
    if not zone_service.delete_zone(zone_id):
        raise HTTPException(status_code=404, detail="Зона не найдена")
    return {"message": "Зона успешно удалена", "zone_id": zone_id}

