"""Сервис для отправки уведомлений клиентам"""

import json
import base64
import asyncio
from typing import Dict, List, Optional
from datetime import datetime
from pathlib import Path
import threading

from fastapi import WebSocket, WebSocketDisconnect
from app.services.monitoring_service import monitoring_service, Violation
from app.api.clients import clients_storage
from app.utils.logger import logger


class NotificationService:
    """Сервис для управления уведомлениями"""
    
    def __init__(self):
        # Активные WebSocket соединения: client_id -> WebSocket
        self.active_connections: Dict[str, WebSocket] = {}
        self.lock = threading.Lock()
        self.notification_timeout = 300  # 5 минут
        self.app_event_loop: Optional[asyncio.AbstractEventLoop] = None
    
    async def connect(self, client_id: str, websocket: WebSocket):
        """Подключение клиента через WebSocket"""
        await websocket.accept()
        with self.lock:
            self.active_connections[client_id] = websocket
            # Сохраняем event loop приложения при первом подключении
            if self.app_event_loop is None:
                try:
                    self.app_event_loop = asyncio.get_running_loop()
                    logger.debug(f"Сохранен event loop приложения для уведомлений")
                except RuntimeError:
                    # Если нет running loop, попробуем получить текущий
                    try:
                        self.app_event_loop = asyncio.get_event_loop()
                    except RuntimeError:
                        pass
        logger.info(f"Клиент {client_id} подключен")
    
    def disconnect(self, client_id: str):
        """Отключение клиента"""
        with self.lock:
            if client_id in self.active_connections:
                # Просто удаляем из списка, WebSocket закроется автоматически при завершении приложения
                del self.active_connections[client_id]
                logger.info(f"Клиент {client_id} отключен")
    
    async def send_notification(self, violation: Violation) -> List[str]:
        """
        Отправка уведомления всем подключенным клиентам
        
        Returns:
            Список ID клиентов, которым отправлено уведомление
        """
        # Читаем изображение и кодируем в base64
        image_data = None
        try:
            image_path = Path(violation.image_path)
            if image_path.exists():
                with open(image_path, 'rb') as f:
                    image_bytes = f.read()
                    image_data = base64.b64encode(image_bytes).decode('utf-8')
        except Exception as e:
            logger.warning(f"Ошибка при чтении изображения: {e}")
        
        # Формируем уведомление
        notification = {
            "type": "violation",
            "violation_id": violation.id,
            "timestamp": violation.timestamp,
            "zone_id": violation.zone_id,
            "zone_name": violation.zone_name,
            "detection": violation.detection.to_dict(),
            "image": image_data,  # base64 encoded image
            "image_url": f"/api/violations/{violation.id}/image"
        }
        
        # Отправляем всем подключенным клиентам
        sent_to = []
        disconnected_clients = []
        
        with self.lock:
            clients_to_notify = list(self.active_connections.keys())
        
        for client_id in clients_to_notify:
            try:
                websocket = self.active_connections.get(client_id)
                if websocket:
                    await websocket.send_json(notification)
                    sent_to.append(client_id)
            except Exception as e:
                logger.error(f"Ошибка при отправке уведомления клиенту {client_id}: {e}", exc_info=True)
                disconnected_clients.append(client_id)
        
        # Удаляем отключенных клиентов
        for client_id in disconnected_clients:
            self.disconnect(client_id)
        
        return sent_to
    
    async def handle_response(self, client_id: str, response: dict):
        """Обработка ответа от клиента"""
        violation_id = response.get("violation_id")
        operator_response = response.get("response")  # True для подтверждения, False для ложного срабатывания
        
        if violation_id is None or operator_response is None:
            return False
        
        # Обновляем статус нарушения
        status = "confirmed" if operator_response else "false_positive"
        success = monitoring_service.update_violation_status(
            violation_id=violation_id,
            status=status,
            operator_id=client_id
        )
        
        # Логируем ответ оператора
        if success:
            from app.services.logging_service import logging_service
            logging_service.log_system_event(
                event_type="operator_response",
                message=f"Оператор {client_id} ответил на нарушение {violation_id}",
                metadata={"violation_id": violation_id, "response": operator_response}
            )
        
        return success
    
    def get_connected_clients(self) -> List[str]:
        """Получение списка подключенных клиентов"""
        with self.lock:
            return list(self.active_connections.keys())
    
    async def broadcast_system_message(self, message: str, message_type: str = "info"):
        """Отправка системного сообщения всем клиентам"""
        notification = {
            "type": "system",
            "message_type": message_type,
            "message": message,
            "timestamp": datetime.now().isoformat()
        }
        
        disconnected_clients = []
        
        with self.lock:
            clients = list(self.active_connections.keys())
        
        for client_id in clients:
            try:
                websocket = self.active_connections.get(client_id)
                if websocket:
                    await websocket.send_json(notification)
            except Exception as e:
                logger.error(f"Ошибка при отправке системного сообщения клиенту {client_id}: {e}", exc_info=True)
                disconnected_clients.append(client_id)
        
        for client_id in disconnected_clients:
            self.disconnect(client_id)


# Глобальный экземпляр сервиса
notification_service = NotificationService()

