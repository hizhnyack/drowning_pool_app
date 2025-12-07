# Спецификация Android-клиента для системы мониторинга запретных зон

## 1. Общее описание

Android-приложение предназначено для получения уведомлений о нарушениях в запретных зонах в режиме реального времени, просмотра деталей нарушений и подтверждения/отклонения срабатываний оператором.

### Основные функции:
- Регистрация устройства на сервере
- Получение уведомлений о нарушениях через WebSocket
- Просмотр деталей нарушения с изображением
- Подтверждение или отклонение нарушения (ложное срабатывание)
- Просмотр истории нарушений
- Просмотр статуса сервера и мониторинга

---

## 2. Архитектура приложения

### 2.1. Технологический стек

**Рекомендуемые технологии:**
- **Язык**: Kotlin
- **Минимальная версия Android**: API 24 (Android 7.0)
- **Целевая версия Android**: API 34+ (Android 14+)
- **Архитектура**: MVVM (Model-View-ViewModel)
- **Сетевые запросы**: Retrofit 2 + OkHttp
- **WebSocket**: OkHttp WebSocket или Java-WebSocket
- **JSON парсинг**: Gson или Kotlinx Serialization
- **Изображения**: Glide или Coil
- **База данных**: Room (для локального кэширования)
- **Dependency Injection**: Hilt или Koin
- **Reactive programming**: Kotlin Coroutines + Flow

### 2.2. Структура модулей

```
app/
├── data/
│   ├── api/              # API интерфейсы (Retrofit)
│   ├── websocket/        # WebSocket клиент
│   ├── repository/       # Репозитории для работы с данными
│   └── local/            # Room база данных
├── domain/
│   ├── model/            # Модели данных
│   └── usecase/          # Бизнес-логика
├── presentation/
│   ├── ui/               # UI компоненты (Activities, Fragments)
│   ├── viewmodel/        # ViewModels
│   └── adapter/          # RecyclerView адаптеры
└── di/                   # Dependency Injection модули
```

---

## 3. API Endpoints

### 3.1. Базовый URL

```
http://<SERVER_IP>:8000
```

Где `<SERVER_IP>` - IP-адрес сервера в локальной сети (настраивается пользователем в приложении).

### 3.2. Регистрация клиента

**Endpoint**: `POST /api/register`

**Request Body**:
```json
{
  "device_id": "string",      // Уникальный ID устройства (Android ID или UUID)
  "device_name": "string",    // Название устройства (например, "Samsung Galaxy S21")
  "platform": "android"       // Всегда "android"
}
```

**Response** (200 OK):
```json
{
  "client_id": "uuid-string",
  "device_id": "string",
  "device_name": "string",
  "platform": "android",
  "registered_at": "2024-01-15T10:30:00",
  "last_seen": "2024-01-15T10:30:00"
}
```

**Обработка ошибок**:
- `400 Bad Request` - неверный формат данных
- `500 Internal Server Error` - ошибка сервера

**Примечание**: При первом запуске приложения необходимо зарегистрировать устройство. `client_id` сохраняется локально и используется для WebSocket подключения.

---

### 3.3. Отмена регистрации

**Endpoint**: `POST /api/unregister/{client_id}`

**Response** (200 OK):
```json
{
  "message": "Клиент успешно отменен",
  "client_id": "uuid-string"
}
```

**Обработка ошибок**:
- `404 Not Found` - клиент не найден

---

### 3.4. Получение статуса сервера

**Endpoint**: `GET /api/status`

**Response** (200 OK):
```json
{
  "status": "running",
  "timestamp": "2024-01-15T10:30:00",
  "version": "1.0.0"
}
```

---

### 3.5. Получение списка нарушений

**Endpoint**: `GET /api/violations`

**Query Parameters**:
- `status` (optional): `"pending"`, `"confirmed"`, `"false_positive"`
- `limit` (optional, default: 100): максимальное количество нарушений

**Response** (200 OK):
```json
{
  "violations": [
    {
      "id": "uuid-string",
      "zone_id": "uuid-string",
      "zone_name": "Зона 1",
      "detection": {
        "bbox": [100, 200, 300, 400],
        "confidence": 0.85,
        "center": [200, 300],
        "class_id": 0
      },
      "image_path": "data/violations/violation_123.jpg",
      "timestamp": "2024-01-15T10:30:00",
      "status": "pending",
      "operator_response": null,
      "operator_id": null,
      "response_time": null
    }
  ],
  "total": 50
}
```

---

### 3.6. Получение нарушения по ID

**Endpoint**: `GET /api/violations/{violation_id}`

**Response** (200 OK):
```json
{
  "id": "uuid-string",
  "zone_id": "uuid-string",
  "zone_name": "Зона 1",
  "detection": {
    "bbox": [100, 200, 300, 400],
    "confidence": 0.85,
    "center": [200, 300],
    "class_id": 0
  },
  "image_path": "data/violations/violation_123.jpg",
  "timestamp": "2024-01-15T10:30:00",
  "status": "pending",
  "operator_response": null,
  "operator_id": null,
  "response_time": null
}
```

**Обработка ошибок**:
- `404 Not Found` - нарушение не найдено

---

### 3.7. Получение изображения нарушения

**Endpoint**: `GET /api/violations/{violation_id}/image`

**Response** (200 OK):
- Content-Type: `image/jpeg`
- Body: бинарные данные изображения

**Обработка ошибок**:
- `404 Not Found` - нарушение или изображение не найдено

---

### 3.8. Отправка ответа на уведомление (HTTP fallback)

**Endpoint**: `POST /api/notifications/response`

**Request Body**:
```json
{
  "violation_id": "uuid-string",
  "response": true  // true - подтверждено, false - ложное срабатывание
}
```

**Response** (200 OK):
```json
{
  "message": "Ответ успешно обработан"
}
```

**Обработка ошибок**:
- `404 Not Found` - нарушение не найдено

**Примечание**: Этот endpoint используется как резервный вариант, если WebSocket недоступен. Основной способ отправки ответа - через WebSocket.

---

## 4. WebSocket протокол

### 4.1. Подключение

**URL**: `ws://<SERVER_IP>:8000/api/notifications/ws/{client_id}`

Где `client_id` - ID клиента, полученный при регистрации.

**Протокол**: WebSocket (ws://) или WSS (wss://) для HTTPS

### 4.2. Получение уведомлений

Сервер отправляет уведомления в формате JSON:

```json
{
  "type": "violation",
  "violation_id": "uuid-string",
  "timestamp": "2024-01-15T10:30:00",
  "zone_id": "uuid-string",
  "zone_name": "Зона 1",
  "detection": {
    "bbox": [100, 200, 300, 400],
    "confidence": 0.85,
    "center": [200, 300],
    "class_id": 0
  },
  "image": "base64-encoded-image-string",  // Изображение в base64 (может быть null)
  "image_url": "/api/violations/{violation_id}/image"
}
```

**Примечание**: Поле `image` может быть `null` или отсутствовать. В этом случае изображение нужно загрузить по `image_url`.

### 4.3. Отправка ответа на уведомление

Клиент отправляет ответ в формате JSON:

```json
{
  "type": "response",
  "violation_id": "uuid-string",
  "response": true  // true - подтверждено, false - ложное срабатывание
}
```

### 4.4. Heartbeat (ping/pong)

Для поддержания соединения клиент может отправлять ping:

```json
{
  "type": "ping"
}
```

Сервер отвечает:

```json
{
  "type": "pong"
}
```

**Рекомендация**: Отправлять ping каждые 30 секунд для поддержания соединения.

### 4.5. Обработка разрывов соединения

При разрыве WebSocket соединения:
1. Приложение должно автоматически переподключаться с экспоненциальной задержкой (1s, 2s, 4s, 8s, max 30s)
2. Показывать пользователю статус подключения
3. Сохранять неотправленные ответы локально и отправлять их после переподключения

---

## 5. Модели данных

### 5.1. Client (Клиент)

```kotlin
data class Client(
    val clientId: String,
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val registeredAt: String,  // ISO 8601
    val lastSeen: String       // ISO 8601
)
```

### 5.2. Violation (Нарушение)

```kotlin
data class Violation(
    val id: String,
    val zoneId: String,
    val zoneName: String,
    val detection: Detection,
    val imagePath: String,
    val timestamp: String,           // ISO 8601
    val status: ViolationStatus,     // pending, confirmed, false_positive
    val operatorResponse: Boolean?,  // null если pending
    val operatorId: String?,
    val responseTime: String?        // ISO 8601
)

enum class ViolationStatus {
    PENDING,
    CONFIRMED,
    FALSE_POSITIVE
}
```

### 5.3. Detection (Детекция)

```kotlin
data class Detection(
    val bbox: List<Int>,      // [x1, y1, x2, y2]
    val confidence: Float,
    val center: List<Int>,    // [x, y]
    val classId: Int
)
```

### 5.4. Notification (Уведомление)

```kotlin
data class Notification(
    val type: String,         // "violation"
    val violationId: String,
    val timestamp: String,    // ISO 8601
    val zoneId: String,
    val zoneName: String,
    val detection: Detection,
    val image: String?,       // base64 или null
    val imageUrl: String
)
```

### 5.5. NotificationResponse (Ответ на уведомление)

```kotlin
data class NotificationResponse(
    val type: String,         // "response"
    val violationId: String,
    val response: Boolean     // true - подтверждено, false - ложное срабатывание
)
```

---

## 6. Пользовательский интерфейс

### 6.1. Экран настроек (Settings)

**Функции**:
- Ввод IP-адреса сервера
- Ввод порта сервера (по умолчанию 8000)
- Отображение статуса подключения к серверу
- Кнопка "Подключиться" / "Отключиться"
- Отображение информации о зарегистрированном устройстве (client_id, device_id)

**Элементы UI**:
- TextInput для IP-адреса
- TextInput для порта
- Switch для автоматического подключения при запуске
- Button "Подключиться"
- TextView со статусом подключения (подключен/отключен)
- Card с информацией о клиенте

### 6.2. Главный экран (Main/Dashboard)

**Функции**:
- Отображение статуса мониторинга (активен/неактивен)
- Счетчик неподтвержденных нарушений
- Список последних нарушений (RecyclerView)
- Кнопка обновления списка

**Элементы UI**:
- AppBar с заголовком и кнопкой настроек
- Card со статусом мониторинга
- Badge с количеством неподтвержденных нарушений
- RecyclerView со списком нарушений
- FloatingActionButton для обновления

**Элемент списка нарушений**:
- Миниатюра изображения
- Название зоны
- Время нарушения
- Статус (pending/confirmed/false_positive)
- Уровень уверенности детекции

### 6.3. Экран деталей нарушения (ViolationDetail)

**Функции**:
- Отображение полного изображения нарушения
- Информация о нарушении (зона, время, уверенность)
- Кнопки "Подтвердить" и "Ложное срабатывание" (если статус pending)
- Отображение статуса (если уже обработано)

**Элементы UI**:
- ImageView с изображением нарушения (с возможностью масштабирования)
- Card с информацией:
  - Название зоны
  - Время нарушения
  - Уверенность детекции (%)
  - Координаты детекции
- Button "Подтвердить" (зеленый)
- Button "Ложное срабатывание" (красный)
- TextView со статусом (если обработано)

**Поведение**:
- При нажатии на кнопку отправляется ответ через WebSocket
- После отправки ответа кнопки скрываются, показывается статус
- Изображение загружается по `image_url` или из base64 (если есть)

### 6.4. Экран истории нарушений (ViolationsHistory)

**Функции**:
- Полный список всех нарушений
- Фильтрация по статусу (pending, confirmed, false_positive)
- Поиск по названию зоны
- Сортировка по времени (новые первые)

**Элементы UI**:
- AppBar с заголовком и кнопкой фильтра
- SearchView для поиска
- ChipGroup для фильтров по статусу
- RecyclerView со списком нарушений
- SwipeRefreshLayout для обновления

### 6.5. Уведомления (Notifications)

**Функции**:
- Показ системных уведомлений Android при получении нового нарушения
- При нажатии на уведомление открывается экран деталей нарушения
- Звуковое оповещение (настраиваемое)
- Вибрация (настраиваемое)

**Тип уведомления**:
- Канал: "Violation Alerts"
- Приоритет: HIGH
- Звук: настраиваемый
- Вибрация: настраиваемая
- Действия: "Подтвердить", "Отклонить" (Quick Actions)

---

## 7. Локальное хранилище

### 7.1. Room Database

**Таблицы**:

1. **ViolationEntity** - кэш нарушений
   - id (Primary Key)
   - violationId (String, Unique)
   - zoneId (String)
   - zoneName (String)
   - timestamp (String)
   - status (String)
   - imagePath (String) - локальный путь к изображению
   - detectionJson (String) - JSON строка с детекцией
   - operatorResponse (Boolean?)
   - responseTime (String?)

2. **ClientEntity** - информация о клиенте
   - id (Primary Key)
   - clientId (String, Unique)
   - deviceId (String)
   - deviceName (String)
   - serverIp (String)
   - serverPort (Int)
   - registeredAt (String)

3. **PendingResponseEntity** - неотправленные ответы
   - id (Primary Key)
   - violationId (String)
   - response (Boolean)
   - timestamp (String)

### 7.2. SharedPreferences

**Ключи**:
- `server_ip` - IP-адрес сервера
- `server_port` - порт сервера
- `client_id` - ID клиента
- `auto_connect` - автоматическое подключение
- `notification_sound_enabled` - включен ли звук уведомлений
- `notification_vibration_enabled` - включена ли вибрация

---

## 8. Обработка ошибок

### 8.1. Сетевые ошибки

- **Нет подключения к интернету**: Показать сообщение "Нет подключения к интернету"
- **Сервер недоступен**: Показать сообщение "Сервер недоступен. Проверьте IP-адрес и порт"
- **Таймаут**: Показать сообщение "Превышено время ожидания ответа"
- **Ошибка 404**: Показать сообщение "Ресурс не найден"
- **Ошибка 500**: Показать сообщение "Ошибка сервера"

### 8.2. WebSocket ошибки

- **Разрыв соединения**: Автоматическое переподключение с индикацией статуса
- **Ошибка подключения**: Показать сообщение и предложить проверить настройки
- **Таймаут**: Попытка переподключения

### 8.3. Обработка данных

- **Неверный формат JSON**: Логировать ошибку, пропустить сообщение
- **Отсутствие обязательных полей**: Логировать ошибку, показать уведомление об ошибке

---

## 9. Безопасность

### 9.1. Хранение данных

- Не хранить чувствительные данные в открытом виде
- Использовать Android Keystore для шифрования при необходимости
- Не логировать персональные данные

### 9.2. Сетевое взаимодействие

- Поддержка HTTPS/WSS (если сервер поддерживает)
- Проверка сертификатов (для production)
- Валидация всех входящих данных

### 9.3. Разрешения

**Необходимые разрешения**:
- `INTERNET` - для сетевых запросов
- `ACCESS_NETWORK_STATE` - для проверки подключения
- `VIBRATE` - для вибрации уведомлений (опционально)
- `POST_NOTIFICATIONS` - для Android 13+ (опционально)

---

## 10. Производительность

### 10.1. Оптимизация изображений

- Кэширование изображений с помощью Glide/Coil
- Загрузка изображений в фоновом потоке
- Использование thumbnail для списков
- Lazy loading для истории нарушений

### 10.2. Оптимизация сети

- Кэширование ответов API
- Batch загрузка нарушений (пагинация)
- Использование OkHttp cache

### 10.3. Оптимизация базы данных

- Индексы на часто используемые поля
- Фоновые операции в отдельных потоках
- Очистка старых данных (настраиваемый период)

---

## 11. Тестирование

### 11.1. Unit тесты

- Тесты ViewModels
- Тесты UseCases
- Тесты Repository
- Тесты парсинга JSON

### 11.2. Интеграционные тесты

- Тесты API клиента
- Тесты WebSocket клиента
- Тесты Room Database

### 11.3. UI тесты

- Тесты основных экранов
- Тесты навигации
- Тесты взаимодействия с элементами

---

## 12. Дополнительные функции (опционально)

### 12.1. Статистика

- График нарушений по времени
- Статистика по зонам
- Процент ложных срабатываний

### 12.2. Настройки уведомлений

- Настройка звука уведомлений
- Настройка вибрации
- Фильтрация уведомлений по зонам
- Тихий режим (Do Not Disturb)

### 12.3. Офлайн режим

- Просмотр кэшированных нарушений
- Отправка ответов после восстановления соединения

---

## 13. Примеры использования API

### 13.1. Регистрация клиента

```kotlin
val clientRegister = ClientRegister(
    deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID),
    deviceName = Build.MODEL,
    platform = "android"
)

val response = apiService.registerClient(clientRegister)
// Сохранить response.clientId локально
```

### 13.2. Подключение к WebSocket

```kotlin
val request = Request.Builder()
    .url("ws://$serverIp:$serverPort/api/notifications/ws/$clientId")
    .build()

val webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, text: String) {
        val notification = gson.fromJson(text, Notification::class.java)
        // Обработать уведомление
    }
    
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        // Обработать ошибку, переподключиться
    }
})
```

### 13.3. Отправка ответа

```kotlin
val response = NotificationResponse(
    type = "response",
    violationId = violation.id,
    response = true  // или false
)

webSocket.send(gson.toJson(response))
```

---

## 14. Чеклист разработки

- [ ] Настройка проекта (Kotlin, зависимости)
- [ ] Создание моделей данных
- [ ] Реализация API клиента (Retrofit)
- [ ] Реализация WebSocket клиента
- [ ] Настройка Room Database
- [ ] Реализация Repository слоя
- [ ] Реализация ViewModels
- [ ] Создание UI экранов
- [ ] Реализация уведомлений Android
- [ ] Обработка ошибок и переподключений
- [ ] Тестирование
- [ ] Оптимизация производительности
- [ ] Подготовка к релизу

---

## 15. Контакты и поддержка

При возникновении вопросов по спецификации обращайтесь к разработчикам серверной части.

**Версия спецификации**: 1.0.0  
**Дата последнего обновления**: 2024-01-15

