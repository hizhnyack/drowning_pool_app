"""Конфигурация приложения"""

import json
from pathlib import Path
from typing import Optional
from pydantic_settings import BaseSettings


class Config:
    """Класс для работы с конфигурацией"""
    
    def __init__(self, config_path: str = "config.json"):
        self.config_path = Path(config_path)
        self._config = self._load_config()
    
    def _load_config(self) -> dict:
        """Загрузка конфигурации из файла"""
        if not self.config_path.exists():
            raise FileNotFoundError(f"Конфигурационный файл не найден: {self.config_path}")
        
        with open(self.config_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    
    def get(self, key: str, default=None):
        """Получение значения конфигурации по ключу (поддержка вложенных ключей через точку)"""
        keys = key.split('.')
        value = self._config
        
        for k in keys:
            if isinstance(value, dict) and k in value:
                value = value[k]
            else:
                return default
        
        return value
    
    def get_server_host(self) -> str:
        return self.get('server.host', '0.0.0.0')
    
    def get_server_port(self) -> int:
        return self.get('server.port', 8000)
    
    def get_camera_index(self) -> int:
        return self.get('video.camera_index', 0)
    
    def get_detection_model(self) -> str:
        return self.get('detection.model', 'yolo')
    
    def get_database_path(self) -> str:
        return self.get('database.path', 'data/database.db')
    
    def get_violations_path(self) -> str:
        return self.get('storage.violations_path', 'data/violations')
    
    def get_uploads_path(self) -> str:
        return self.get('storage.uploads_path', 'data/uploads')


# Глобальный экземпляр конфигурации
config = Config()

