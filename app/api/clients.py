"""API endpoints для управления клиентами"""

from fastapi import APIRouter, HTTPException
from typing import List, Dict
from pydantic import BaseModel
from datetime import datetime
import uuid

router = APIRouter()

# Хранилище зарегистрированных клиентов (в памяти, позже перенесем в БД)
clients_storage: Dict[str, dict] = {}


class ClientRegister(BaseModel):
    """Модель регистрации клиента"""
    device_id: str
    device_name: str
    platform: str = "android"


class ClientResponse(BaseModel):
    """Модель ответа с информацией о клиенте"""
    client_id: str
    device_id: str
    device_name: str
    platform: str
    registered_at: str
    last_seen: str


@router.post("/register", response_model=ClientResponse)
async def register_client(client: ClientRegister):
    """Регистрация Android-клиента"""
    client_id = str(uuid.uuid4())
    now = datetime.now().isoformat()
    
    client_data = {
        "client_id": client_id,
        "device_id": client.device_id,
        "device_name": client.device_name,
        "platform": client.platform,
        "registered_at": now,
        "last_seen": now
    }
    
    clients_storage[client_id] = client_data
    
    return ClientResponse(**client_data)


@router.post("/unregister/{client_id}")
async def unregister_client(client_id: str):
    """Отмена регистрации клиента"""
    if client_id not in clients_storage:
        raise HTTPException(status_code=404, detail="Клиент не найден")
    
    del clients_storage[client_id]
    return {"message": "Клиент успешно отменен", "client_id": client_id}


@router.get("/clients", response_model=List[ClientResponse])
async def get_clients():
    """Получение списка зарегистрированных клиентов"""
    return [ClientResponse(**client) for client in clients_storage.values()]


@router.get("/clients/{client_id}", response_model=ClientResponse)
async def get_client(client_id: str):
    """Получение информации о конкретном клиенте"""
    if client_id not in clients_storage:
        raise HTTPException(status_code=404, detail="Клиент не найден")
    
    return ClientResponse(**clients_storage[client_id])

