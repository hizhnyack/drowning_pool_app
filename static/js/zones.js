// JavaScript для управления зонами
console.log('zones.js: Файл загружен!', new Date().toISOString());

let isEditMode = false;
let currentZone = null;
let drawingZone = null;
let zonePoints = [];
let isDrawing = false;

// Инициализация
console.log('zones.js: Регистрация обработчика DOMContentLoaded, readyState:', document.readyState);
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        console.log('zones.js: DOMContentLoaded сработал, настройка обработчиков событий');
        setTimeout(() => setupZoneEventListeners(), 100); // Небольшая задержка для гарантии
    });
} else {
    // DOM уже загружен
    console.log('zones.js: DOM уже загружен, сразу настраиваем обработчики');
    setTimeout(() => setupZoneEventListeners(), 100); // Небольшая задержка для гарантии
}

function setupZoneEventListeners() {
    console.log('zones.js: setupZoneEventListeners() вызвана');
    
    const addZoneBtn = document.getElementById('addZone');
    const editZonesBtn = document.getElementById('editZones');
    const saveZonesBtn = document.getElementById('saveZones');
    const cancelEditBtn = document.getElementById('cancelEdit');
    
    console.log('zones.js: Поиск элементов:', {
        addZone: !!addZoneBtn,
        editZones: !!editZonesBtn,
        saveZones: !!saveZonesBtn,
        cancelEdit: !!cancelEditBtn
    });
    
    if (addZoneBtn) {
        console.log('zones.js: Привязка обработчика к кнопке "Добавить зону"');
        // Пробуем несколько способов привязки для надежности
        addZoneBtn.onclick = function(e) {
            console.log('zones.js: КНОПКА "Добавить зону" НАЖАТА (onclick)!', e);
            e.preventDefault();
            e.stopPropagation();
            startAddingZone();
            return false;
        };
        addZoneBtn.addEventListener('click', function(e) {
            console.log('zones.js: КНОПКА "Добавить зону" НАЖАТА (addEventListener)!', e);
            e.preventDefault();
            e.stopPropagation();
            startAddingZone();
            return false;
        }, true); // Используем capture phase
        console.log('zones.js: Обработчики привязаны к addZone (и onclick, и addEventListener)');
    } else {
        console.error('zones.js: ОШИБКА - элемент addZone не найден!');
        console.error('zones.js: Попытка найти через querySelector...');
        const altBtn = document.querySelector('#addZone');
        console.error('zones.js: querySelector результат:', altBtn);
        if (altBtn) {
            console.log('zones.js: Найден через querySelector, привязываем обработчик');
            altBtn.onclick = function(e) {
                console.log('zones.js: КНОПКА "Добавить зону" НАЖАТА (querySelector)!', e);
                startAddingZone();
            };
        }
    }
    
    if (editZonesBtn) {
        editZonesBtn.addEventListener('click', (e) => {
            console.log('zones.js: Кнопка "Редактировать зоны" нажата');
            toggleEditMode();
        });
    }
    
    if (saveZonesBtn) {
        saveZonesBtn.addEventListener('click', (e) => {
            console.log('zones.js: Кнопка "Сохранить" нажата');
            saveZones();
        });
    }
    
    if (cancelEditBtn) {
        cancelEditBtn.addEventListener('click', (e) => {
            console.log('zones.js: Кнопка "Отмена" нажата');
            cancelEdit();
        });
    }
    
    const canvas = document.getElementById('zonesCanvas');
    if (canvas) {
        console.log('zones.js: Canvas найден, привязка обработчиков событий');
        canvas.addEventListener('click', (e) => {
            console.log('zones.js: Клик на canvas', e);
            handleCanvasClick(e);
        });
        canvas.addEventListener('mousemove', handleCanvasMouseMove);
        canvas.style.pointerEvents = 'auto';
        console.log('zones.js: Обработчики canvas привязаны');
    } else {
        console.error('zones.js: ОШИБКА - элемент zonesCanvas не найден!');
    }
}

// Начало добавления новой зоны
function startAddingZone() {
    console.log('zones.js: startAddingZone() вызвана');
    console.log('zones.js: Текущее состояние:', { isEditMode, isDrawing, zonePoints: zonePoints.length });
    
    // Автоматически включаем режим редактирования, если он не включен
    if (!isEditMode) {
        console.log('zones.js: Включение режима редактирования');
        isEditMode = true;
        const saveBtn = document.getElementById('saveZones');
        const cancelBtn = document.getElementById('cancelEdit');
        if (saveBtn) {
            saveBtn.style.display = 'inline-block';
            console.log('zones.js: Кнопка "Сохранить" показана');
        }
        if (cancelBtn) {
            cancelBtn.style.display = 'inline-block';
            console.log('zones.js: Кнопка "Отмена" показана');
        }
    }
    
    isDrawing = true;
    zonePoints = [];
    drawingZone = null;
    console.log('zones.js: Режим рисования включен, массив точек очищен');
    
    const canvas = document.getElementById('zonesCanvas');
    if (canvas) {
        console.log('zones.js: Canvas найден, установка курсора');
        canvas.style.cursor = 'crosshair';
        canvas.style.pointerEvents = 'auto';
        console.log('zones.js: Курсор установлен на crosshair, pointerEvents = auto');
    } else {
        console.error('zones.js: ОШИБКА - canvas не найден!');
        alert('Ошибка: canvas для рисования зон не найден');
        return;
    }
    
    // Удаляем предыдущую инструкцию, если есть
    const oldInstruction = document.getElementById('zoneInstruction');
    if (oldInstruction && oldInstruction.parentNode) {
        oldInstruction.parentNode.removeChild(oldInstruction);
    }
    
    // Показываем инструкцию
    console.log('zones.js: Создание элемента инструкции');
    const instruction = document.createElement('div');
    instruction.id = 'zoneInstruction';
    instruction.style.cssText = 'position: fixed; top: 20px; right: 20px; background: #3498db; color: white; padding: 15px; border-radius: 8px; z-index: 1000; max-width: 300px; box-shadow: 0 4px 6px rgba(0,0,0,0.3);';
    instruction.innerHTML = `
        <strong>Создание зоны (0 точек)</strong><br>
        Кликните на видео, чтобы добавить точки зоны.<br>
        Минимум 3 точки.<br>
        Двойной клик или кнопка "Завершить" - завершить создание.<br>
        <button id="finishZoneBtn" style="margin-top: 10px; padding: 5px 10px; cursor: pointer; background: #27ae60; color: white; border: none; border-radius: 4px;">Завершить</button>
        <button id="cancelZoneBtn" style="margin-top: 10px; margin-left: 5px; padding: 5px 10px; cursor: pointer; background: #e74c3c; color: white; border: none; border-radius: 4px;">Отмена</button>
    `;
    document.body.appendChild(instruction);
    console.log('zones.js: Инструкция добавлена в DOM');
    
    const finishBtn = document.getElementById('finishZoneBtn');
    const cancelBtn = document.getElementById('cancelZoneBtn');
    
    if (finishBtn) {
        finishBtn.addEventListener('click', () => {
            console.log('zones.js: Кнопка "Завершить" нажата');
            finishDrawingZone();
            if (instruction.parentNode) {
                instruction.parentNode.removeChild(instruction);
            }
        });
        console.log('zones.js: Обработчик привязан к finishZoneBtn');
    } else {
        console.error('zones.js: ОШИБКА - finishZoneBtn не найден!');
    }
    
    if (cancelBtn) {
        cancelBtn.addEventListener('click', () => {
            console.log('zones.js: Кнопка "Отмена" нажата');
            zonePoints = [];
            isDrawing = false;
            if (canvas) {
                canvas.style.cursor = 'default';
            }
            if (typeof drawZones === 'function') {
                drawZones();
            }
            if (instruction.parentNode) {
                instruction.parentNode.removeChild(instruction);
            }
        });
        console.log('zones.js: Обработчик привязан к cancelZoneBtn');
    } else {
        console.error('zones.js: ОШИБКА - cancelZoneBtn не найден!');
    }
    
    console.log('zones.js: startAddingZone() завершена, готово к рисованию');
}

// Обработка клика на canvas
function handleCanvasClick(event) {
    if (!isDrawing && !isEditMode) return;
    
    const canvas = document.getElementById('zonesCanvas');
    const video = document.getElementById('videoStream');
    
    if (!canvas || !video) return;
    
    const rect = canvas.getBoundingClientRect();
    
    // Получаем реальные размеры видео
    const videoRect = video.getBoundingClientRect();
    const videoWidth = video.videoWidth || videoRect.width;
    const videoHeight = video.videoHeight || videoRect.height;
    
    const scaleX = videoWidth / videoRect.width;
    const scaleY = videoHeight / videoRect.height;
    
    // Координаты клика относительно canvas
    const clickX = event.clientX - rect.left;
    const clickY = event.clientY - rect.top;
    
    // Преобразуем в координаты видео
    const x = clickX * scaleX;
    const y = clickY * scaleY;
    
    if (isDrawing) {
        // Добавляем точку
        zonePoints.push({ x: Math.round(x), y: Math.round(y) });
        console.log(`Добавлена точка ${zonePoints.length}: (${Math.round(x)}, ${Math.round(y)})`);
        drawCurrentZone();
        
        // Обновляем инструкцию
        const instruction = document.getElementById('zoneInstruction');
        if (instruction) {
            const pointsInfo = instruction.querySelector('strong');
            if (pointsInfo) {
                pointsInfo.textContent = `Создание зоны (${zonePoints.length} точек)`;
            }
        }
        
        // Двойной клик - завершить (если минимум 3 точки)
        if (event.detail === 2 && zonePoints.length >= 3) {
            finishDrawingZone();
        }
    } else if (isEditMode) {
        // Редактирование существующей зоны
        const clickedZone = findZoneAtPoint(x, y);
        if (clickedZone) {
            selectZoneForEdit(clickedZone);
        }
    }
}

// Обработка движения мыши
function handleCanvasMouseMove(event) {
    if (!isDrawing) return;
    
    const canvas = document.getElementById('zonesCanvas');
    const video = document.getElementById('videoStream');
    
    if (!canvas || !video) return;
    
    const rect = canvas.getBoundingClientRect();
    
    // Получаем реальные размеры видео
    const videoRect = video.getBoundingClientRect();
    const videoWidth = video.videoWidth || videoRect.width;
    const videoHeight = video.videoHeight || videoRect.height;
    
    const scaleX = videoWidth / videoRect.width;
    const scaleY = videoHeight / videoRect.height;
    
    // Координаты мыши относительно canvas
    const mouseX = event.clientX - rect.left;
    const mouseY = event.clientY - rect.top;
    
    // Преобразуем в координаты видео
    const x = mouseX * scaleX;
    const y = mouseY * scaleY;
    
    // Обновляем предпросмотр
    drawCurrentZone({ x: Math.round(x), y: Math.round(y) });
}

// Рисование текущей зоны
function drawCurrentZone(previewPoint = null) {
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
    
    // Устанавливаем размеры canvas
    canvas.width = videoRect.width;
    canvas.height = videoRect.height;
    canvas.style.width = videoRect.width + 'px';
    canvas.style.height = videoRect.height + 'px';
    
    // Очищаем canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    // Рисуем существующие зоны
    if (typeof drawZones === 'function') {
        drawZones();
    }
    
    // Рисуем текущую зону
    if (zonePoints.length > 0) {
        ctx.strokeStyle = '#e74c3c';
        ctx.lineWidth = 3;
        ctx.fillStyle = 'rgba(231, 76, 60, 0.3)';
        
        ctx.beginPath();
        const firstPoint = zonePoints[0];
        ctx.moveTo(firstPoint.x / scaleX, firstPoint.y / scaleY);
        
        for (let i = 1; i < zonePoints.length; i++) {
            ctx.lineTo(zonePoints[i].x / scaleX, zonePoints[i].y / scaleY);
        }
        
        if (previewPoint) {
            ctx.lineTo(previewPoint.x / scaleX, previewPoint.y / scaleY);
            ctx.lineTo(firstPoint.x / scaleX, firstPoint.y / scaleY);
        } else if (zonePoints.length >= 3) {
            // Замыкаем полигон, если есть минимум 3 точки
            ctx.lineTo(firstPoint.x / scaleX, firstPoint.y / scaleY);
        }
        
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
        
        // Рисуем точки
        zonePoints.forEach((point, index) => {
            // Круг для точки
            ctx.fillStyle = '#e74c3c';
            ctx.beginPath();
            ctx.arc(point.x / scaleX, point.y / scaleY, 6, 0, 2 * Math.PI);
            ctx.fill();
            
            // Обводка точки
            ctx.strokeStyle = '#fff';
            ctx.lineWidth = 2;
            ctx.stroke();
            
            // Номер точки
            ctx.fillStyle = '#fff';
            ctx.font = 'bold 12px Arial';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText((index + 1).toString(), point.x / scaleX, point.y / scaleY);
        });
        
        if (previewPoint) {
            ctx.fillStyle = '#e74c3c';
            ctx.beginPath();
            ctx.arc(previewPoint.x / scaleX, previewPoint.y / scaleY, 6, 0, 2 * Math.PI);
            ctx.fill();
            ctx.strokeStyle = '#fff';
            ctx.lineWidth = 2;
            ctx.stroke();
        }
    }
}

// Завершение рисования зоны
async function finishDrawingZone() {
    if (zonePoints.length < 3) {
        alert('Зона должна содержать минимум 3 точки');
        return;
    }
    
    const zoneName = prompt('Введите название зоны:');
    if (!zoneName) {
        zonePoints = [];
        drawCurrentZone();
        return;
    }
    
    try {
        const response = await fetch('/api/zones/', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name: zoneName,
                points: zonePoints
            })
        });
        
        if (response.ok) {
            await loadZones();
            zonePoints = [];
            isDrawing = false;
            const canvas = document.getElementById('zonesCanvas');
            canvas.style.cursor = 'default';
        } else {
            const error = await response.json();
            alert('Ошибка при создании зоны: ' + (error.detail || 'Неизвестная ошибка'));
        }
    } catch (error) {
        console.error('Ошибка при создании зоны:', error);
        alert('Ошибка при создании зоны');
    }
}

// Поиск зоны в точке
function findZoneAtPoint(x, y) {
    // Упрощенная проверка - ищем ближайшую точку зоны
    for (const zone of zones) {
        for (const point of zone.points) {
            const distance = Math.sqrt(Math.pow(point.x - x, 2) + Math.pow(point.y - y, 2));
            if (distance < 20) {
                return zone;
            }
        }
    }
    return null;
}

// Выбор зоны для редактирования
function selectZoneForEdit(zone) {
    currentZone = zone;
    highlightZone(zone.id);
    // Можно добавить UI для редактирования
}

// Переключение режима редактирования
function toggleEditMode() {
    isEditMode = !isEditMode;
    
    const saveBtn = document.getElementById('saveZones');
    const cancelBtn = document.getElementById('cancelEdit');
    
    if (isEditMode) {
        saveBtn.style.display = 'inline-block';
        cancelBtn.style.display = 'inline-block';
    } else {
        saveBtn.style.display = 'none';
        cancelBtn.style.display = 'none';
        isDrawing = false;
        zonePoints = [];
        const canvas = document.getElementById('zonesCanvas');
        if (canvas) {
            canvas.style.cursor = 'default';
        }
    }
}

// Сохранение зон
async function saveZones() {
    // Зоны уже сохранены через API при создании/редактировании
    alert('Зоны сохранены');
    toggleEditMode();
}

// Отмена редактирования
function cancelEdit() {
    isDrawing = false;
    zonePoints = [];
    currentZone = null;
    toggleEditMode();
    if (typeof drawZones === 'function') {
        drawZones();
    }
}

// Переопределение функции editZone из main.js
window.editZone = async function(zoneId) {
    if (!isEditMode) {
        alert('Сначала включите режим редактирования');
        return;
    }
    
    const zone = zones.find(z => z.id === zoneId);
    if (!zone) return;
    
    const newName = prompt('Введите новое название зоны:', zone.name);
    if (!newName || newName === zone.name) return;
    
    try {
        const response = await fetch(`/api/zones/${zoneId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name: newName })
        });
        
        if (response.ok) {
            await loadZones();
        } else {
            alert('Ошибка при обновлении зоны');
        }
    } catch (error) {
        console.error('Ошибка при обновлении зоны:', error);
        alert('Ошибка при обновлении зоны');
    }
};

