// JavaScript для страницы логов

let currentPage = 0;
const pageSize = 20;
let currentFilters = {};

// Инициализация
document.addEventListener('DOMContentLoaded', async () => {
    await loadZones();
    await loadStatistics();
    await loadLogs();
    setupEventListeners();
});

function setupEventListeners() {
    document.getElementById('applyFilters').addEventListener('click', applyFilters);
    document.getElementById('resetFilters').addEventListener('click', resetFilters);
    document.getElementById('exportCsv').addEventListener('click', () => exportLogs('csv'));
    document.getElementById('exportJson').addEventListener('click', () => exportLogs('json'));
    document.getElementById('prevPage').addEventListener('click', () => changePage(-1));
    document.getElementById('nextPage').addEventListener('click', () => changePage(1));
    
    // Модальное окно
    const modal = document.getElementById('imageModal');
    const closeBtn = document.querySelector('.close');
    closeBtn.addEventListener('click', () => {
        modal.style.display = 'none';
    });
    window.addEventListener('click', (event) => {
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    });
}

// Загрузка зон для фильтра
async function loadZones() {
    try {
        const response = await fetch('/api/zones/');
        const zones = await response.json();
        
        const select = document.getElementById('filterZone');
        zones.forEach(zone => {
            const option = document.createElement('option');
            option.value = zone.id;
            option.textContent = zone.name;
            select.appendChild(option);
        });
    } catch (error) {
        console.error('Ошибка при загрузке зон:', error);
    }
}

// Загрузка статистики
async function loadStatistics() {
    try {
        const response = await fetch('/api/logs/stats');
        const stats = await response.json();
        
        document.getElementById('statTotal').textContent = stats.total || 0;
        document.getElementById('statConfirmed').textContent = stats.confirmed || 0;
        document.getElementById('statFalse').textContent = stats.false_positive || 0;
        document.getElementById('statPending').textContent = stats.pending || 0;
    } catch (error) {
        console.error('Ошибка при загрузке статистики:', error);
    }
}

// Загрузка логов
async function loadLogs() {
    try {
        const params = new URLSearchParams({
            limit: pageSize,
            offset: currentPage * pageSize
        });
        
        if (currentFilters.status) {
            params.append('status', currentFilters.status);
        }
        if (currentFilters.zone_id) {
            params.append('zone_id', currentFilters.zone_id);
        }
        if (currentFilters.start_date) {
            params.append('start_date', currentFilters.start_date);
        }
        if (currentFilters.end_date) {
            params.append('end_date', currentFilters.end_date);
        }
        
        const response = await fetch(`/api/logs/?${params}`);
        const data = await response.json();
        
        renderLogsTable(data.violations);
        updatePagination(data.total);
    } catch (error) {
        console.error('Ошибка при загрузке логов:', error);
        document.getElementById('logsTableBody').innerHTML = 
            '<tr><td colspan="7">Ошибка при загрузке данных</td></tr>';
    }
}

// Отображение таблицы логов
function renderLogsTable(violations) {
    const tbody = document.getElementById('logsTableBody');
    
    if (violations.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7">Нет данных</td></tr>';
        return;
    }
    
    tbody.innerHTML = violations.map(violation => {
        const date = new Date(violation.timestamp);
        const dateStr = date.toLocaleString('ru-RU');
        
        const statusClass = violation.status === 'confirmed' ? 'confirmed' : 
                           violation.status === 'false_positive' ? 'false_positive' : 'pending';
        const statusText = violation.status === 'confirmed' ? 'Подтверждено' :
                          violation.status === 'false_positive' ? 'Ложное' : 'Ожидает';
        
        return `
            <tr>
                <td>${dateStr}</td>
                <td>
                    <img src="/api/violations/${violation.id}/image" 
                         alt="Нарушение" 
                         onclick="showImageModal('/api/violations/${violation.id}/image')"
                         style="cursor: pointer;">
                </td>
                <td>${violation.zone_name}</td>
                <td><span class="status-badge ${statusClass}">${statusText}</span></td>
                <td>${(violation.detection.confidence * 100).toFixed(1)}%</td>
                <td>${violation.operator_id || '-'}</td>
                <td>
                    <button class="btn btn-secondary" onclick="viewViolation('${violation.id}')">Подробнее</button>
                    <button class="btn btn-danger" onclick="deleteViolation('${violation.id}')" style="margin-left: 5px;">Удалить</button>
                </td>
            </tr>
        `;
    }).join('');
}

// Обновление пагинации
function updatePagination(total) {
    const totalPages = Math.ceil(total / pageSize);
    document.getElementById('pageInfo').textContent = 
        `Страница ${currentPage + 1} из ${totalPages || 1}`;
    
    document.getElementById('prevPage').disabled = currentPage === 0;
    document.getElementById('nextPage').disabled = currentPage >= totalPages - 1;
}

// Смена страницы
function changePage(delta) {
    currentPage += delta;
    if (currentPage < 0) currentPage = 0;
    loadLogs();
}

// Применение фильтров
function applyFilters() {
    currentFilters = {
        status: document.getElementById('filterStatus').value || null,
        zone_id: document.getElementById('filterZone').value || null,
        start_date: document.getElementById('filterStartDate').value || null,
        end_date: document.getElementById('filterEndDate').value || null
    };
    
    // Преобразуем даты в ISO формат
    if (currentFilters.start_date) {
        currentFilters.start_date = new Date(currentFilters.start_date).toISOString();
    }
    if (currentFilters.end_date) {
        currentFilters.end_date = new Date(currentFilters.end_date + 'T23:59:59').toISOString();
    }
    
    currentPage = 0;
    loadLogs();
    loadStatistics();
}

// Сброс фильтров
function resetFilters() {
    document.getElementById('filterStatus').value = '';
    document.getElementById('filterZone').value = '';
    document.getElementById('filterStartDate').value = '';
    document.getElementById('filterEndDate').value = '';
    currentFilters = {};
    currentPage = 0;
    loadLogs();
    loadStatistics();
}

// Экспорт логов
function exportLogs(format) {
    const params = new URLSearchParams({ format });
    
    if (currentFilters.start_date) {
        params.append('start_date', currentFilters.start_date);
    }
    if (currentFilters.end_date) {
        params.append('end_date', currentFilters.end_date);
    }
    
    window.location.href = `/api/logs/export?${params}`;
}

// Просмотр изображения в модальном окне
function showImageModal(imageUrl) {
    const modal = document.getElementById('imageModal');
    const img = document.getElementById('modalImage');
    img.src = imageUrl;
    modal.style.display = 'block';
}

// Просмотр деталей нарушения
function viewViolation(violationId) {
    window.location.href = `/api/violations/${violationId}`;
}

// Удаление нарушения
async function deleteViolation(violationId) {
    if (!confirm('Вы уверены, что хотите удалить это нарушение?')) {
        return;
    }
    
    try {
        const response = await fetch(`/api/violations/${violationId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            // Перезагружаем список и статистику
            await loadLogs();
            await loadStatistics();
            alert('Нарушение успешно удалено');
        } else {
            const error = await response.json();
            alert(`Ошибка при удалении: ${error.detail || 'Неизвестная ошибка'}`);
        }
    } catch (error) {
        console.error('Ошибка при удалении нарушения:', error);
        alert('Ошибка при удалении нарушения');
    }
}

