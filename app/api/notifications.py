"""API endpoints для уведомлений"""

from fastapi import APIRouter, WebSocket, WebSocketDisconnect, HTTPException
from pydantic import BaseModel
from typing import List

from app.services.notification_service import notification_service
from app.services.monitoring_service import monitoring_service

router = APIRouter()


class NotificationResponse(BaseModel):
    """Модель ответа на уведомление"""
    violation_id: str
    response: bool  # True - подтверждено, False - ложное срабатывание


@router.websocket("/ws/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_id: str):
    """WebSocket endpoint для получения уведомлений"""
    await notification_service.connect(client_id, websocket)
    
    try:
        while True:
            # Ожидаем сообщения от клиента (ответы на уведомления)
            data = await websocket.receive_json()
            
            if data.get("type") == "response":
                await notification_service.handle_response(client_id, data)
            elif data.get("type") == "ping":
                # Heartbeat для поддержания соединения
                await websocket.send_json({"type": "pong"})
    except WebSocketDisconnect:
        notification_service.disconnect(client_id)
    except Exception as e:
        print(f"Ошибка в WebSocket соединении: {e}")
        notification_service.disconnect(client_id)


@router.post("/response")
async def handle_notification_response(response: NotificationResponse):
    """Обработка ответа на уведомление (HTTP fallback)"""
    success = await notification_service.handle_response(
        client_id="http_client",
        response={
            "violation_id": response.violation_id,
            "response": response.response
        }
    )
    
    if success:
        return {"message": "Ответ успешно обработан"}
    else:
        raise HTTPException(status_code=404, detail="Нарушение не найдено")


@router.get("/clients")
async def get_connected_clients():
    """Получение списка подключенных клиентов"""
    return {
        "connected_clients": notification_service.get_connected_clients(),
        "total": len(notification_service.get_connected_clients())
    }


@router.post("/test")
async def send_test_notification():
    """Отправка тестового уведомления всем подключенным клиентам"""
    from app.services.detection_service import Detection
    from app.services.monitoring_service import Violation
    from datetime import datetime
    import uuid
    import numpy as np
    from pathlib import Path
    
    # Создаем тестовое нарушение
    test_detection = Detection(
        bbox=(100, 100, 300, 400),
        confidence=0.95,
        class_id=0
    )
    
    # Создаем тестовое изображение (пустой файл или используем существующее)
    violations_path = Path("data/violations")
    violations_path.mkdir(parents=True, exist_ok=True)
    
    # Пытаемся найти существующее изображение нарушения
    test_image_path = None
    existing_violations = monitoring_service.get_all_violations()
    if existing_violations:
        # Используем изображение последнего нарушения
        test_image_path = existing_violations[0].image_path
    else:
        # Создаем пустое изображение
        test_image_path = str(violations_path / f"test_{datetime.now().strftime('%Y%m%d_%H%M%S')}.jpg")
        # Создаем простое тестовое изображение
        import cv2
        test_img = np.zeros((480, 640, 3), dtype=np.uint8)
        cv2.putText(test_img, "TEST NOTIFICATION", (50, 240), 
                   cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 2)
        cv2.imwrite(test_image_path, test_img)
    
    test_violation = Violation(
        zone_id="test-zone",
        zone_name="Тестовая зона",
        detection=test_detection,
        image_path=test_image_path
    )
    
    # Отправляем уведомление
    sent_to = await notification_service.send_notification(test_violation)
    
    return {
        "message": "Тестовое уведомление отправлено",
        "violation_id": test_violation.id,
        "sent_to": sent_to,
        "total_clients": len(sent_to)
    }

