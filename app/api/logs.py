"""API endpoints для логов"""

from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import Response
from typing import Optional

from app.services.logging_service import logging_service

router = APIRouter()


@router.get("/")
async def get_logs(
    status: Optional[str] = Query(None, description="Фильтр по статусу"),
    zone_id: Optional[str] = Query(None, description="Фильтр по зоне"),
    start_date: Optional[str] = Query(None, description="Начальная дата (ISO format)"),
    end_date: Optional[str] = Query(None, description="Конечная дата (ISO format)"),
    limit: int = Query(100, ge=1, le=1000, description="Лимит записей"),
    offset: int = Query(0, ge=0, description="Смещение")
):
    """Получение логов с фильтрацией и пагинацией"""
    violations = logging_service.get_violations(
        status=status,
        zone_id=zone_id,
        start_date=start_date,
        end_date=end_date,
        limit=limit,
        offset=offset
    )
    
    total = logging_service.get_violations_count(
        status=status,
        zone_id=zone_id,
        start_date=start_date,
        end_date=end_date
    )
    
    return {
        "violations": violations,
        "total": total,
        "limit": limit,
        "offset": offset
    }


@router.get("/stats")
async def get_statistics(
    start_date: Optional[str] = Query(None, description="Начальная дата (ISO format)"),
    end_date: Optional[str] = Query(None, description="Конечная дата (ISO format)")
):
    """Получение статистики по нарушениям"""
    stats = logging_service.get_statistics(start_date=start_date, end_date=end_date)
    return stats


@router.get("/export")
async def export_logs(
    format: str = Query("csv", regex="^(csv|json)$", description="Формат экспорта"),
    start_date: Optional[str] = Query(None, description="Начальная дата (ISO format)"),
    end_date: Optional[str] = Query(None, description="Конечная дата (ISO format)")
):
    """Экспорт логов"""
    if format == "csv":
        content = logging_service.export_violations_csv(
            start_date=start_date,
            end_date=end_date
        )
        return Response(
            content=content,
            media_type="text/csv",
            headers={"Content-Disposition": "attachment; filename=violations.csv"}
        )
    elif format == "json":
        content = logging_service.export_violations_json(
            start_date=start_date,
            end_date=end_date
        )
        return Response(
            content=content,
            media_type="application/json",
            headers={"Content-Disposition": "attachment; filename=violations.json"}
        )

