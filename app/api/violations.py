"""API endpoints для нарушений"""

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse
from typing import List, Optional
from pathlib import Path

from app.services.monitoring_service import monitoring_service

router = APIRouter()


@router.get("/")
async def get_violations(status: Optional[str] = None, limit: int = 100):
    """Получение списка нарушений с фильтрацией"""
    from app.services.logging_service import logging_service
    
    # Получаем нарушения из базы данных (основной источник истины)
    violations_from_db = logging_service.get_violations(
        status=status,
        limit=limit,
        offset=0
    )
    
    # Получаем общее количество для подсчета total
    total_count = logging_service.get_violations_count(status=status)
    
    return {
        "violations": violations_from_db,
        "total": total_count
    }


@router.get("/{violation_id}")
async def get_violation(violation_id: str):
    """Получение нарушения по ID"""
    from app.services.logging_service import logging_service
    
    # Сначала проверяем в базе данных
    violation = logging_service.get_violation_by_id(violation_id)
    
    # Если нет в базе, проверяем в памяти (на случай, если сервер не перезапускался)
    if violation is None:
        violation_in_memory = monitoring_service.get_violation(violation_id)
        if violation_in_memory is None:
            raise HTTPException(status_code=404, detail="Нарушение не найдено")
        return violation_in_memory.to_dict()
    
    return violation


@router.get("/{violation_id}/image")
async def get_violation_image(violation_id: str):
    """Получение изображения нарушения"""
    from app.services.logging_service import logging_service
    
    # Сначала проверяем в базе данных
    violation = logging_service.get_violation_by_id(violation_id)
    
    # Если нет в базе, проверяем в памяти
    if violation is None:
        violation_in_memory = monitoring_service.get_violation(violation_id)
        if violation_in_memory is None:
            raise HTTPException(status_code=404, detail="Нарушение не найдено")
        image_path_str = violation_in_memory.image_path
    else:
        image_path_str = violation.get("image_path")
    
    if not image_path_str:
        raise HTTPException(status_code=404, detail="Путь к изображению не найден")
    
    image_path = Path(image_path_str)
    if not image_path.exists():
        raise HTTPException(status_code=404, detail="Изображение не найдено")
    
    return FileResponse(
        path=str(image_path),
        media_type="image/jpeg",
        filename=image_path.name
    )


@router.delete("/{violation_id}")
async def delete_violation(violation_id: str):
    """Удаление нарушения"""
    from app.services.logging_service import logging_service
    from app.utils.logger import logger
    
    # Сначала проверяем в базе данных (основной источник истины)
    violation_from_db = logging_service.get_violation_by_id(violation_id)
    
    # Также проверяем в памяти (может быть, если сервер не перезапускался)
    violation_in_memory = monitoring_service.get_violation(violation_id)
    
    # Если нарушения нет ни в базе, ни в памяти - возвращаем 404
    if violation_from_db is None and violation_in_memory is None:
        raise HTTPException(status_code=404, detail="Нарушение не найдено")
    
    # Определяем путь к изображению (приоритет - из базы данных)
    image_path_str = None
    if violation_from_db:
        image_path_str = violation_from_db.get("image_path")
    elif violation_in_memory:
        image_path_str = violation_in_memory.image_path
    
    # Удаляем из памяти (если есть)
    if violation_in_memory:
        success = monitoring_service.delete_violation(violation_id)
        if not success:
            logger.warning(f"Не удалось удалить нарушение {violation_id} из памяти")
    
    # Удаляем из базы данных
    db_success = logging_service.delete_violation(violation_id)
    if not db_success:
        raise HTTPException(status_code=500, detail="Ошибка при удалении нарушения из базы данных")
    
    # Удаляем изображение, если оно существует
    if image_path_str:
        try:
            image_path = Path(image_path_str)
            if image_path.exists():
                image_path.unlink()
                logger.info(f"Изображение {image_path} удалено")
        except Exception as e:
            logger.warning(f"Не удалось удалить изображение {image_path_str}: {e}")
    
    return {"message": "Нарушение успешно удалено", "violation_id": violation_id}

