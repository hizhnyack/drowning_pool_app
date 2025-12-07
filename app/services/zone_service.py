"""Сервис для работы с запретными зонами"""

import json
from pathlib import Path
from typing import List, Optional, Tuple
from datetime import datetime
import uuid
import threading

from app.models.zone import Zone, ZoneCreate, ZoneUpdate, Point
from app.core.config import config


class ZoneService:
    """Сервис для управления запретными зонами"""
    
    def __init__(self):
        self.zones: dict = {}
        self.lock = threading.Lock()
        self.storage_path = Path("data/zones.json")
        self._load_zones()
    
    def _load_zones(self):
        """Загрузка зон из файла"""
        if self.storage_path.exists():
            try:
                with open(self.storage_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    for zone_id, zone_data in data.items():
                        # Преобразуем точки в объекты Point
                        zone_data['points'] = [Point(**p) for p in zone_data['points']]
                        self.zones[zone_id] = Zone(**zone_data)
            except Exception as e:
                print(f"Ошибка при загрузке зон: {e}")
                self.zones = {}
        else:
            # Создаем директорию если не существует
            self.storage_path.parent.mkdir(parents=True, exist_ok=True)
    
    def _save_zones(self):
        """Сохранение зон в файл"""
        try:
            data = {}
            for zone_id, zone in self.zones.items():
                data[zone_id] = {
                    "id": zone.id,
                    "name": zone.name,
                    "points": [{"x": p.x, "y": p.y} for p in zone.points],
                    "created_at": zone.created_at,
                    "updated_at": zone.updated_at
                }
            
            with open(self.storage_path, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        except Exception as e:
            print(f"Ошибка при сохранении зон: {e}")
    
    def create_zone(self, zone_data: ZoneCreate) -> Zone:
        """Создание новой зоны"""
        with self.lock:
            zone_id = str(uuid.uuid4())
            now = datetime.now().isoformat()
            
            zone = Zone(
                id=zone_id,
                name=zone_data.name,
                points=zone_data.points,
                created_at=now,
                updated_at=now
            )
            
            self.zones[zone_id] = zone
            self._save_zones()
            
            return zone
    
    def get_zone(self, zone_id: str) -> Optional[Zone]:
        """Получение зоны по ID"""
        with self.lock:
            return self.zones.get(zone_id)
    
    def get_all_zones(self) -> List[Zone]:
        """Получение всех зон"""
        with self.lock:
            return list(self.zones.values())
    
    def update_zone(self, zone_id: str, zone_data: ZoneUpdate) -> Optional[Zone]:
        """Обновление зоны"""
        with self.lock:
            if zone_id not in self.zones:
                return None
            
            zone = self.zones[zone_id]
            
            if zone_data.name is not None:
                zone.name = zone_data.name
            
            if zone_data.points is not None:
                zone.points = zone_data.points
            
            zone.updated_at = datetime.now().isoformat()
            
            self._save_zones()
            return zone
    
    def delete_zone(self, zone_id: str) -> bool:
        """Удаление зоны"""
        with self.lock:
            if zone_id not in self.zones:
                return False
            
            del self.zones[zone_id]
            self._save_zones()
            return True
    
    def point_in_polygon(self, point: Tuple[int, int], polygon_points: List[Point]) -> bool:
        """
        Проверка попадания точки в полигон (алгоритм Ray Casting)
        
        Args:
            point: Координаты точки (x, y)
            polygon_points: Список точек полигона
        
        Returns:
            True если точка внутри полигона, False иначе
        """
        x, y = point
        n = len(polygon_points)
        inside = False
        
        p1x, p1y = polygon_points[0].x, polygon_points[0].y
        for i in range(1, n + 1):
            p2x, p2y = polygon_points[i % n].x, polygon_points[i % n].y
            if y > min(p1y, p2y):
                if y <= max(p1y, p2y):
                    if x <= max(p1x, p2x):
                        if p1y != p2y:
                            xinters = (y - p1y) * (p2x - p1x) / (p2y - p1y) + p1x
                        if p1x == p2x or x <= xinters:
                            inside = not inside
            p1x, p1y = p2x, p2y
        
        return inside
    
    def bbox_intersects_zone(self, bbox: Tuple[int, int, int, int], zone: Zone) -> bool:
        """
        Проверка пересечения bounding box с зоной
        
        Args:
            bbox: Bounding box (x1, y1, x2, y2)
            zone: Зона для проверки
        
        Returns:
            True если есть пересечение, False иначе
        """
        x1, y1, x2, y2 = bbox
        
        # Проверяем центр масс
        center = ((x1 + x2) // 2, (y1 + y2) // 2)
        if self.point_in_polygon(center, zone.points):
            return True
        
        # Проверяем углы bounding box
        corners = [
            (x1, y1),  # левый верхний
            (x2, y1),  # правый верхний
            (x2, y2),  # правый нижний
            (x1, y2)   # левый нижний
        ]
        
        for corner in corners:
            if self.point_in_polygon(corner, zone.points):
                return True
        
        # Проверяем пересечение с границами зоны (упрощенная проверка)
        # Если хотя бы одна точка зоны внутри bbox
        for point in zone.points:
            if x1 <= point.x <= x2 and y1 <= point.y <= y2:
                return True
        
        return False
    
    def check_violation(self, detections: List, zones: Optional[List[Zone]] = None) -> List[dict]:
        """
        Проверка нарушений - попадание детекций в зоны
        
        Args:
            detections: Список детекций (объекты с атрибутом bbox)
            zones: Список зон для проверки (если None, проверяются все)
        
        Returns:
            Список нарушений с информацией о зоне и детекции
        """
        if zones is None:
            zones = self.get_all_zones()
        
        violations = []
        
        for zone in zones:
            for detection in detections:
                if hasattr(detection, 'bbox'):
                    if self.bbox_intersects_zone(detection.bbox, zone):
                        violations.append({
                            "zone_id": zone.id,
                            "zone_name": zone.name,
                            "detection": detection.to_dict() if hasattr(detection, 'to_dict') else {
                                "bbox": detection.bbox,
                                "center": getattr(detection, 'center', None),
                                "confidence": getattr(detection, 'confidence', 0.0)
                            }
                        })
        
        return violations


# Глобальный экземпляр сервиса
zone_service = ZoneService()

