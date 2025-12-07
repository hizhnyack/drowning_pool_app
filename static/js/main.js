// Основной JavaScript для управления интерфейсом
console.log('main.js: Файл загружен!');

let currentModel = null;
let isMonitoring = false;
let zones = [];
// isEditMode теперь в zones.js, не объявляем здесь

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', async () => {
    await loadModels();
    await window.loadZones();
    await checkMonitoringStatus();
    setupEventListeners();
});

// Настройка обработчиков событий
function setupEventListeners() {
    // Управление видео
    document.getElementById('startCamera').addEventListener('click', startCamera);
    document.getElementById('playVideo').addEventListener('click', playVideo);
    document.getElementById('pauseVideo').addEventListener('click', pauseVideo);
    document.getElementById('stopVideo').addEventListener('click', stopVideo);
    
    // Загрузка видео
    document.getElementById('uploadVideo').addEventListener('click', () => {
        document.getElementById('videoFile').click();
    });
    document.getElementById('videoFile').addEventListener('change', handleVideoUpload);
    
    // Модель детекции
    document.getElementById('detectionModel').addEventListener('change', handleModelChange);
    
    // Мониторинг
    document.getElementById('startMonitoring').addEventListener('click', startMonitoring);
    document.getElementById('stopMonitoring').addEventListener('click', stopMonitoring);
    document.getElementById('sendTestNotification').addEventListener('click', sendTestNotification);
}

// Загрузка списка моделей
async function loadModels() {
    try {
        const response = await fetch('/api/models/');
        const models = await response.json();
        
        const select = document.getElementById('detectionModel');
        models.forEach(model => {
            const option = document.createElement('option');
            option.value = model.name;
            option.textContent = model.display_name;
            select.appendChild(option);
        });
        
        // Загружаем текущую модель
        const currentResponse = await fetch('/api/models/current');
        const current = await currentResponse.json();
        if (current.current_model) {
            select.value = current.current_model;
            currentModel = current.current_model;
            updateModelStatus(true);
        }
    } catch (error) {
        console.error('Ошибка при загрузке моделей:', error);
    }
}

// Загрузка зон (глобальная функция для использования в других скриптах)
window.loadZones = async function() {
    try {
        const response = await fetch('/api/zones/');
        zones = await response.json();
        renderZonesList();
        drawZones();
    } catch (error) {
        console.error('Ошибка при загрузке зон:', error);
    }
};

// Проверка статуса мониторинга
async function checkMonitoringStatus() {
    try {
        const response = await fetch('/api/monitoring/status');
        const status = await response.json();
        isMonitoring = status.is_monitoring;
        updateMonitoringStatus();
    } catch (error) {
        console.error('Ошибка при проверке статуса мониторинга:', error);
    }
}

// Управление видео
async function startCamera() {
    try {
        const response = await fetch('/api/video/control?action=start_camera', { method: 'POST' });
        const result = await response.json();
        console.log('Камера запущена:', result);
    } catch (error) {
        console.error('Ошибка при запуске камеры:', error);
        alert('Ошибка при запуске камеры');
    }
}

async function playVideo() {
    try {
        const response = await fetch('/api/video/control?action=play', { method: 'POST' });
        const result = await response.json();
        console.log('Воспроизведение:', result);
    } catch (error) {
        console.error('Ошибка при воспроизведении:', error);
    }
}

async function pauseVideo() {
    try {
        const response = await fetch('/api/video/control?action=pause', { method: 'POST' });
        const result = await response.json();
        console.log('Пауза:', result);
    } catch (error) {
        console.error('Ошибка при паузе:', error);
    }
}

async function stopVideo() {
    try {
        const response = await fetch('/api/video/control?action=stop', { method: 'POST' });
        const result = await response.json();
        console.log('Остановка:', result);
    } catch (error) {
        console.error('Ошибка при остановке:', error);
    }
}

// Загрузка видеофайла
async function handleVideoUpload(event) {
    const file = event.target.files[0];
    if (!file) return;
    
    const formData = new FormData();
    formData.append('file', file);
    
    try {
        const response = await fetch('/api/video/upload', {
            method: 'POST',
            body: formData
        });
        const result = await response.json();
        document.getElementById('videoFileName').textContent = file.name;
        console.log('Видео загружено:', result);
    } catch (error) {
        console.error('Ошибка при загрузке видео:', error);
        alert('Ошибка при загрузке видео');
    }
}

// Изменение модели детекции
async function handleModelChange(event) {
    const modelName = event.target.value;
    if (!modelName) return;
    
    try {
        updateModelStatus(false, 'Загрузка...');
        const response = await fetch('/api/models/set', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ model: modelName })
        });
        const result = await response.json();
        currentModel = modelName;
        updateModelStatus(true);
        console.log('Модель установлена:', result);
    } catch (error) {
        console.error('Ошибка при установке модели:', error);
        updateModelStatus(false);
        alert('Ошибка при установке модели');
    }
}

function updateModelStatus(active, message = null) {
    const statusEl = document.getElementById('modelStatus');
    if (active) {
        statusEl.textContent = message || `Модель активна: ${currentModel}`;
        statusEl.className = 'status-indicator active';
    } else {
        statusEl.textContent = message || 'Модель не активна';
        statusEl.className = 'status-indicator inactive';
    }
}

// Управление мониторингом
async function startMonitoring() {
    try {
        const response = await fetch('/api/monitoring/start', { method: 'POST' });
        const result = await response.json();
        isMonitoring = true;
        updateMonitoringStatus();
        console.log('Мониторинг запущен:', result);
    } catch (error) {
        console.error('Ошибка при запуске мониторинга:', error);
        alert('Ошибка при запуске мониторинга');
    }
}

async function stopMonitoring() {
    try {
        const response = await fetch('/api/monitoring/stop', { method: 'POST' });
        const result = await response.json();
        isMonitoring = false;
        updateMonitoringStatus();
        console.log('Мониторинг остановлен:', result);
    } catch (error) {
        console.error('Ошибка при остановке мониторинга:', error);
    }
}

// Отправка тестового уведомления
async function sendTestNotification() {
    try {
        const button = document.getElementById('sendTestNotification');
        button.disabled = true;
        button.textContent = 'Отправка...';
        
        const response = await fetch('/api/notifications/test', { method: 'POST' });
        const result = await response.json();
        
        if (response.ok) {
            alert(`Тестовое уведомление отправлено ${result.total_clients} клиенту(ам)`);
        } else {
            alert(`Ошибка: ${result.detail || 'Неизвестная ошибка'}`);
        }
        
        button.disabled = false;
        button.textContent = 'Отправить тест';
    } catch (error) {
        console.error('Ошибка при отправке тестового уведомления:', error);
        alert('Ошибка при отправке тестового уведомления');
        const button = document.getElementById('sendTestNotification');
        button.disabled = false;
        button.textContent = 'Отправить тест';
    }
}

function updateMonitoringStatus() {
    const statusEl = document.getElementById('monitoringStatus');
    if (isMonitoring) {
        statusEl.textContent = 'Мониторинг активен';
        statusEl.className = 'status-indicator active';
    } else {
        statusEl.textContent = 'Мониторинг остановлен';
        statusEl.className = 'status-indicator inactive';
    }
}

// Отображение списка зон
function renderZonesList() {
    const listEl = document.getElementById('zonesList');
    listEl.innerHTML = '';
    
    zones.forEach(zone => {
        const zoneEl = document.createElement('div');
        zoneEl.className = 'zone-item';
        zoneEl.dataset.zoneId = zone.id;
        zoneEl.innerHTML = `
            <h4>${zone.name}</h4>
            <p>Точек: ${zone.points.length}</p>
            <div class="zone-actions">
                <button class="btn btn-secondary" onclick="editZone('${zone.id}')">Редактировать</button>
                <button class="btn btn-danger" onclick="deleteZone('${zone.id}')">Удалить</button>
            </div>
        `;
        zoneEl.addEventListener('click', () => highlightZone(zone.id));
        listEl.appendChild(zoneEl);
    });
}

// Выделение зоны на видео
function highlightZone(zoneId) {
    zones.forEach(zone => {
        const el = document.querySelector(`[data-zone-id="${zone.id}"]`);
        if (el) {
            el.classList.toggle('active', zone.id === zoneId);
        }
    });
    drawZones(zoneId);
}

// Рисование зон на canvas
function drawZones(highlightZoneId = null) {
    const canvas = document.getElementById('zonesCanvas');
    const video = document.getElementById('videoStream');
    
    if (!canvas || !video) return;
    
    const ctx = canvas.getContext('2d');
    
    // Получаем размеры видео
    const videoRect = video.getBoundingClientRect();
    const videoWidth = video.videoWidth || videoRect.width;
    const videoHeight = video.videoHeight || videoRect.height;
    
    const scaleX = videoWidth / videoRect.width;
    const scaleY = videoHeight / videoRect.height;
    
    // Устанавливаем размеры canvas равными размерам видео на экране
    canvas.width = videoRect.width;
    canvas.height = videoRect.height;
    canvas.style.width = videoRect.width + 'px';
    canvas.style.height = videoRect.height + 'px';
    
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    zones.forEach(zone => {
        if (!zone.points || zone.points.length < 3) return;
        
        const isHighlighted = zone.id === highlightZoneId;
        ctx.strokeStyle = isHighlighted ? '#27ae60' : '#3498db';
        ctx.lineWidth = isHighlighted ? 3 : 2;
        ctx.fillStyle = isHighlighted ? 'rgba(39, 174, 96, 0.2)' : 'rgba(52, 152, 219, 0.1)';
        
        ctx.beginPath();
        const firstPoint = zone.points[0];
        ctx.moveTo(firstPoint.x / scaleX, firstPoint.y / scaleY);
        
        for (let i = 1; i < zone.points.length; i++) {
            ctx.lineTo(zone.points[i].x / scaleX, zone.points[i].y / scaleY);
        }
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
        
        // Подпись зоны
        ctx.fillStyle = isHighlighted ? '#27ae60' : '#3498db';
        ctx.font = 'bold 14px Arial';
        ctx.fillText(zone.name, firstPoint.x / scaleX + 5, firstPoint.y / scaleY - 5);
    });
}

// Обновление canvas при изменении размера видео
const video = document.getElementById('videoStream');
if (video) {
    video.addEventListener('load', () => drawZones());
    setInterval(() => drawZones(), 1000); // Обновление каждую секунду
}

// Экспорт функций для использования в других скриптах
window.editZone = function(zoneId) {
    // Будет реализовано в zones.js
    console.log('Редактирование зоны:', zoneId);
};

window.deleteZone = async function(zoneId) {
    if (!confirm('Вы уверены, что хотите удалить эту зону?')) return;
    
    try {
        const response = await fetch(`/api/zones/${zoneId}`, { method: 'DELETE' });
        if (response.ok) {
            await loadZones();
        }
    } catch (error) {
        console.error('Ошибка при удалении зоны:', error);
        alert('Ошибка при удалении зоны');
    }
};

