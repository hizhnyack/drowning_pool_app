"""Сервис для логирования событий системы"""

import sqlite3
import json
from pathlib import Path
from typing import List, Optional, Dict
from datetime import datetime
from contextlib import contextmanager
import threading

from app.core.config import config


class LoggingService:
    """Сервис для работы с логами"""
    
    def __init__(self):
        self.db_path = Path(config.get_database_path())
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()
        self._init_database()
    
    def _init_database(self):
        """Инициализация базы данных"""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            
            # Таблица нарушений
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS violations (
                    id TEXT PRIMARY KEY,
                    zone_id TEXT NOT NULL,
                    zone_name TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    image_path TEXT NOT NULL,
                    detection_bbox TEXT NOT NULL,
                    detection_confidence REAL NOT NULL,
                    detection_center TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'pending',
                    operator_response INTEGER,
                    operator_id TEXT,
                    response_time TEXT
                )
            """)
            
            # Таблица ответов операторов
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS operator_responses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    violation_id TEXT NOT NULL,
                    operator_id TEXT NOT NULL,
                    response INTEGER NOT NULL,
                    timestamp TEXT NOT NULL,
                    FOREIGN KEY (violation_id) REFERENCES violations(id)
                )
            """)
            
            # Таблица системных событий
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS system_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_type TEXT NOT NULL,
                    message TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    metadata TEXT
                )
            """)
            
            conn.commit()
    
    @contextmanager
    def _get_connection(self):
        """Контекстный менеджер для работы с БД"""
        conn = sqlite3.connect(str(self.db_path))
        conn.row_factory = sqlite3.Row
        try:
            yield conn
        finally:
            conn.close()
    
    def log_violation(self, violation: Dict):
        """Логирование нарушения"""
        with self.lock:
            with self._get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    INSERT OR REPLACE INTO violations 
                    (id, zone_id, zone_name, timestamp, image_path, 
                     detection_bbox, detection_confidence, detection_center,
                     status, operator_response, operator_id, response_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    violation['id'],
                    violation['zone_id'],
                    violation['zone_name'],
                    violation['timestamp'],
                    violation['image_path'],
                    json.dumps(violation['detection']['bbox']),
                    violation['detection']['confidence'],
                    json.dumps(violation['detection']['center']),
                    violation.get('status', 'pending'),
                    violation.get('operator_response'),
                    violation.get('operator_id'),
                    violation.get('response_time')
                ))
                conn.commit()
    
    def update_violation_status(self, violation_id: str, status: str, 
                                operator_id: Optional[str] = None, 
                                operator_response: Optional[bool] = None):
        """Обновление статуса нарушения"""
        with self.lock:
            with self._get_connection() as conn:
                cursor = conn.cursor()
                response_time = datetime.now().isoformat()
                
                cursor.execute("""
                    UPDATE violations 
                    SET status = ?, operator_id = ?, operator_response = ?, response_time = ?
                    WHERE id = ?
                """, (status, operator_id, operator_response, response_time, violation_id))
                
                # Логируем ответ оператора
                if operator_id and operator_response is not None:
                    cursor.execute("""
                        INSERT INTO operator_responses 
                        (violation_id, operator_id, response, timestamp)
                        VALUES (?, ?, ?, ?)
                    """, (violation_id, operator_id, 1 if operator_response else 0, response_time))
                
                conn.commit()
    
    def get_violation_by_id(self, violation_id: str) -> Optional[Dict]:
        """Получение нарушения по ID из базы данных"""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM violations WHERE id = ?", (violation_id,))
            row = cursor.fetchone()
            if row is None:
                return None
            
            # Преобразуем строку в словарь
            return {
                "id": row["id"],
                "zone_id": row["zone_id"],
                "zone_name": row["zone_name"],
                "timestamp": row["timestamp"],
                "image_path": row["image_path"],
                "detection": {
                    "bbox": json.loads(row["detection_bbox"]),
                    "confidence": row["detection_confidence"],
                    "center": json.loads(row["detection_center"]),
                    "class_id": 0
                },
                "status": row["status"],
                "operator_response": bool(row["operator_response"]) if row["operator_response"] is not None else None,
                "operator_id": row["operator_id"],
                "response_time": row["response_time"]
            }
    
    def delete_violation(self, violation_id: str) -> bool:
        """Удаление нарушения из базы данных"""
        with self.lock:
            with self._get_connection() as conn:
                cursor = conn.cursor()
                # Проверяем, существует ли нарушение
                cursor.execute("SELECT id FROM violations WHERE id = ?", (violation_id,))
                if cursor.fetchone() is None:
                    return False
                
                # Удаляем нарушение
                cursor.execute("DELETE FROM violations WHERE id = ?", (violation_id,))
                # Удаляем связанные ответы операторов
                cursor.execute("DELETE FROM operator_responses WHERE violation_id = ?", (violation_id,))
                conn.commit()
                return True
    
    def log_system_event(self, event_type: str, message: str, metadata: Optional[Dict] = None):
        """Логирование системного события"""
        with self.lock:
            with self._get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    INSERT INTO system_events (event_type, message, timestamp, metadata)
                    VALUES (?, ?, ?, ?)
                """, (
                    event_type,
                    message,
                    datetime.now().isoformat(),
                    json.dumps(metadata) if metadata else None
                ))
                conn.commit()
    
    def get_violations(self, status: Optional[str] = None, 
                      zone_id: Optional[str] = None,
                      start_date: Optional[str] = None,
                      end_date: Optional[str] = None,
                      limit: int = 100,
                      offset: int = 0) -> List[Dict]:
        """Получение нарушений с фильтрацией"""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            
            query = "SELECT * FROM violations WHERE 1=1"
            params = []
            
            if status:
                query += " AND status = ?"
                params.append(status)
            
            if zone_id:
                query += " AND zone_id = ?"
                params.append(zone_id)
            
            if start_date:
                query += " AND timestamp >= ?"
                params.append(start_date)
            
            if end_date:
                query += " AND timestamp <= ?"
                params.append(end_date)
            
            query += " ORDER BY timestamp DESC LIMIT ? OFFSET ?"
            params.extend([limit, offset])
            
            cursor.execute(query, params)
            rows = cursor.fetchall()
            
            violations = []
            for row in rows:
                violation = dict(row)
                violation['detection'] = {
                    'bbox': json.loads(violation['detection_bbox']),
                    'confidence': violation['detection_confidence'],
                    'center': json.loads(violation['detection_center'])
                }
                del violation['detection_bbox']
                del violation['detection_confidence']
                del violation['detection_center']
                violations.append(violation)
            
            return violations
    
    def get_violations_count(self, status: Optional[str] = None,
                            zone_id: Optional[str] = None,
                            start_date: Optional[str] = None,
                            end_date: Optional[str] = None) -> int:
        """Получение количества нарушений"""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            
            query = "SELECT COUNT(*) as count FROM violations WHERE 1=1"
            params = []
            
            if status:
                query += " AND status = ?"
                params.append(status)
            
            if zone_id:
                query += " AND zone_id = ?"
                params.append(zone_id)
            
            if start_date:
                query += " AND timestamp >= ?"
                params.append(start_date)
            
            if end_date:
                query += " AND timestamp <= ?"
                params.append(end_date)
            
            cursor.execute(query, params)
            result = cursor.fetchone()
            return result['count'] if result else 0
    
    def get_statistics(self, start_date: Optional[str] = None,
                      end_date: Optional[str] = None) -> Dict:
        """Получение статистики по нарушениям"""
        with self._get_connection() as conn:
            cursor = conn.cursor()
            
            date_filter = ""
            params = []
            if start_date:
                date_filter += " AND timestamp >= ?"
                params.append(start_date)
            if end_date:
                date_filter += " AND timestamp <= ?"
                params.append(end_date)
            
            # Общее количество
            cursor.execute(f"SELECT COUNT(*) as count FROM violations WHERE 1=1 {date_filter}", params)
            total = cursor.fetchone()['count']
            
            # Подтвержденные
            cursor.execute(f"SELECT COUNT(*) as count FROM violations WHERE status = 'confirmed' {date_filter}", params)
            confirmed = cursor.fetchone()['count']
            
            # Ложные срабатывания
            cursor.execute(f"SELECT COUNT(*) as count FROM violations WHERE status = 'false_positive' {date_filter}", params)
            false_positive = cursor.fetchone()['count']
            
            # Ожидающие ответа
            cursor.execute(f"SELECT COUNT(*) as count FROM violations WHERE status = 'pending' {date_filter}", params)
            pending = cursor.fetchone()['count']
            
            return {
                "total": total,
                "confirmed": confirmed,
                "false_positive": false_positive,
                "pending": pending
            }
    
    def export_violations_csv(self, start_date: Optional[str] = None,
                             end_date: Optional[str] = None) -> str:
        """Экспорт нарушений в CSV"""
        import csv
        import io
        
        violations = self.get_violations(start_date=start_date, end_date=end_date, limit=10000)
        
        output = io.StringIO()
        writer = csv.writer(output)
        
        # Заголовки
        writer.writerow([
            'ID', 'Zone ID', 'Zone Name', 'Timestamp', 'Status',
            'Operator Response', 'Operator ID', 'Response Time',
            'Confidence', 'BBox X1', 'BBox Y1', 'BBox X2', 'BBox Y2'
        ])
        
        # Данные
        for v in violations:
            bbox = v['detection']['bbox']
            writer.writerow([
                v['id'],
                v['zone_id'],
                v['zone_name'],
                v['timestamp'],
                v['status'],
                v.get('operator_response'),
                v.get('operator_id'),
                v.get('response_time'),
                v['detection']['confidence'],
                bbox[0], bbox[1], bbox[2], bbox[3]
            ])
        
        return output.getvalue()
    
    def export_violations_json(self, start_date: Optional[str] = None,
                              end_date: Optional[str] = None) -> str:
        """Экспорт нарушений в JSON"""
        violations = self.get_violations(start_date=start_date, end_date=end_date, limit=10000)
        return json.dumps(violations, ensure_ascii=False, indent=2)


# Глобальный экземпляр сервиса
logging_service = LoggingService()

