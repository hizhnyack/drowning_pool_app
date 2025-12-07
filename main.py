"""Точка входа приложения"""

import uvicorn
import signal
import sys
import os
from app.core.config import config
from app.utils.logger import logger

# Глобальная переменная для хранения сервера
server = None
shutdown_event = None


def signal_handler(sig, frame):
    """Обработчик сигналов для корректного завершения"""
    logger.info("Получен сигнал завершения (Ctrl+C). Останавливаем сервер...")
    
    # Устанавливаем флаг shutdown в приложении
    try:
        from app.main import app_shutting_down
        import app.main
        app.main.app_shutting_down = True
        logger.info("Флаг shutdown установлен в приложении")
    except Exception as e:
        logger.warning(f"Не удалось установить флаг shutdown: {e}")
    
    if server:
        try:
            # Устанавливаем флаг завершения
            server.should_exit = True
            # Пытаемся остановить сервер через asyncio
            if hasattr(server, 'force_exit'):
                server.force_exit = True
        except Exception as e:
            logger.warning(f"Ошибка при попытке остановить сервер: {e}")
    
    # Принудительный выход через 3 секунды, если сервер не остановился
    import threading
    def force_exit():
        import time
        time.sleep(3)
        logger.warning("Принудительное завершение процесса (таймаут)")
        os._exit(0)
    
    force_thread = threading.Thread(target=force_exit, daemon=True)
    force_thread.start()
    
    # Пытаемся корректно завершиться
    try:
        sys.exit(0)
    except:
        os._exit(0)


if __name__ == "__main__":
    # Регистрируем обработчики сигналов ДО запуска сервера
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    host = config.get_server_host()
    port = config.get_server_port()
    
    logger.info(f"Запуск сервера на {host}:{port}")
    logger.info("Для остановки нажмите Ctrl+C")
    
    try:
        config_uvicorn = uvicorn.Config(
            "app.main:app",
            host=host,
            port=port,
            reload=config.get('server.debug', False),
            log_level="info",
            # Включаем graceful shutdown с увеличенным таймаутом
            timeout_graceful_shutdown=5.0
        )
        server = uvicorn.Server(config_uvicorn)
        
        # Запускаем сервер
        server.run()
        
    except KeyboardInterrupt:
        logger.info("Сервер остановлен пользователем (KeyboardInterrupt)")
    except Exception as e:
        logger.error(f"Ошибка при запуске сервера: {e}", exc_info=True)
        sys.exit(1)
    finally:
        logger.info("Сервер завершил работу")

