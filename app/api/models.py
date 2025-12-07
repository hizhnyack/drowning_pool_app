"""API endpoints для управления моделями детекции"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import List

from app.services.detection_service import detection_service, DetectionModel

router = APIRouter()


class ModelInfo(BaseModel):
    """Информация о модели"""
    name: str
    display_name: str
    description: str


class SetModelRequest(BaseModel):
    """Запрос на установку модели"""
    model: str


class ModelConfig(BaseModel):
    """Конфигурация модели"""
    confidence_threshold: float
    iou_threshold: float


@router.get("/", response_model=List[ModelInfo])
async def get_models():
    """Получение списка доступных моделей"""
    return [
        ModelInfo(
            name="yolo",
            display_name="YOLO v8",
            description="YOLOv8 - быстрая и точная модель детекции объектов"
        ),
        ModelInfo(
            name="mediapipe",
            display_name="MediaPipe Pose",
            description="MediaPipe Pose - детекция позы человека"
        )
    ]


@router.get("/current")
async def get_current_model():
    """Получение информации о текущей модели"""
    return detection_service.get_model_info()


@router.post("/set")
async def set_model(request: SetModelRequest):
    """Установка модели детекции"""
    if detection_service.set_model(request.model):
        return {
            "message": f"Модель {request.model} успешно установлена",
            "info": detection_service.get_model_info()
        }
    else:
        raise HTTPException(
            status_code=400,
            detail=f"Не удалось установить модель {request.model}. Доступные модели: yolo, mediapipe"
        )


@router.post("/config")
async def set_model_config(config: ModelConfig):
    """Установка параметров модели"""
    detection_service.set_confidence_threshold(config.confidence_threshold)
    detection_service.set_iou_threshold(config.iou_threshold)
    
    return {
        "message": "Параметры модели обновлены",
        "info": detection_service.get_model_info()
    }

