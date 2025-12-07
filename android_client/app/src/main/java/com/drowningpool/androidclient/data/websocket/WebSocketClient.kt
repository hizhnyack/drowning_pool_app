package com.drowningpool.androidclient.data.websocket

import com.drowningpool.androidclient.domain.model.Notification
import com.drowningpool.androidclient.domain.model.NotificationResponse
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class WebSocketClient(
    private val gson: Gson
) {
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelays = listOf(1L, 2L, 4L, 8L, 30L) // секунды
    private var serverUrl: String? = null
    private var clientId: String? = null
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var isExplicitlyDisconnected = false // Флаг явного отключения
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _notifications = MutableStateFlow<Notification?>(null)
    val notifications: StateFlow<Notification?> = _notifications.asStateFlow()
    
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            android.util.Log.d("WebSocketClient", "Connection opened successfully")
            reconnectAttempts = 0
            isExplicitlyDisconnected = false
            _connectionState.value = ConnectionState.Connected
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                android.util.Log.d("WebSocketClient", "Received message: $text")
                val message = gson.fromJson(text, Map::class.java)
                when (message["type"]) {
                    "violation" -> {
                        android.util.Log.d("WebSocketClient", "Processing violation notification")
                        val notification = gson.fromJson(text, Notification::class.java)
                        android.util.Log.d("WebSocketClient", "Notification parsed: violationId=${notification.violationId}, zoneName=${notification.zoneName}")
                        _notifications.value = notification
                    }
                    "pong" -> {
                        // Heartbeat ответ
                        android.util.Log.d("WebSocketClient", "Received pong")
                    }
                    else -> {
                        android.util.Log.d("WebSocketClient", "Unknown message type: ${message["type"]}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WebSocketClient", "Error processing message", e)
                e.printStackTrace()
            }
        }
        
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // Бинарные сообщения не используются
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            _connectionState.value = ConnectionState.Disconnected
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            android.util.Log.d("WebSocketClient", "Connection closed: code=$code, reason=$reason, explicit=$isExplicitlyDisconnected")
            _connectionState.value = ConnectionState.Disconnected
            // Переподключаемся только если это не было явное отключение
            if (!isExplicitlyDisconnected) {
                attemptReconnect()
            }
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            android.util.Log.e("WebSocketClient", "Connection failed: ${t.message}, explicit=$isExplicitlyDisconnected", t)
            _connectionState.value = ConnectionState.Error(t.message ?: "Unknown error")
            // Переподключаемся только если это не было явное отключение
            if (!isExplicitlyDisconnected) {
                attemptReconnect()
            }
        }
    }
    
    fun connect(serverUrl: String, clientId: String) {
        // Если уже подключены к тому же серверу и клиенту, не переподключаемся
        if (_connectionState.value is ConnectionState.Connected && 
            this.serverUrl == serverUrl && 
            this.clientId == clientId) {
            android.util.Log.d("WebSocketClient", "Already connected to $serverUrl/$clientId")
            return
        }
        
        // Если уже подключаемся, не делаем ничего
        if (_connectionState.value is ConnectionState.Connecting) {
            android.util.Log.d("WebSocketClient", "Already connecting, skipping")
            return
        }
        
        android.util.Log.d("WebSocketClient", "Connecting to $serverUrl/$clientId")
        
        // Отключаемся перед подключением (но не переподключаемся)
        isExplicitlyDisconnected = true
        disconnectInternal()
        isExplicitlyDisconnected = false
        
        this.serverUrl = serverUrl
        this.clientId = clientId
        reconnectAttempts = 0
        
        // Создаем клиент один раз и переиспользуем
        if (client == null) {
            client = OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        }
        
        val request = Request.Builder()
            .url("$serverUrl/api/notifications/ws/$clientId")
            .build()
        
        _connectionState.value = ConnectionState.Connecting
        webSocket = client?.newWebSocket(request, listener)
    }
    
    fun disconnect() {
        android.util.Log.d("WebSocketClient", "Explicit disconnect called")
        isExplicitlyDisconnected = true
        disconnectInternal()
    }
    
    private fun disconnectInternal() {
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        // Не обнуляем client, чтобы переиспользовать
        // client = null
        reconnectAttempts = 0
        // Не обнуляем serverUrl и clientId при явном отключении, чтобы можно было переподключиться
        if (isExplicitlyDisconnected) {
            serverUrl = null
            clientId = null
        }
        _connectionState.value = ConnectionState.Disconnected
    }
    
    fun sendResponse(response: NotificationResponse): Boolean {
        return try {
            val json = gson.toJson(response)
            webSocket?.send(json) ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    fun sendPing(): Boolean {
        return try {
            val ping = gson.toJson(mapOf("type" to "ping"))
            webSocket?.send(ping) ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun attemptReconnect() {
        reconnectJob?.cancel()
        
        if (reconnectAttempts >= maxReconnectAttempts) {
            _connectionState.value = ConnectionState.Error("Max reconnect attempts reached")
            return
        }
        
        val delay = reconnectDelays[reconnectAttempts.coerceAtMost(reconnectDelays.size - 1)]
        reconnectAttempts++
        
        val url = serverUrl
        val id = clientId
        
        if (url != null && id != null) {
            reconnectJob = CoroutineScope(Dispatchers.IO).launch {
                delay(delay * 1000)
                connect(url, id)
            }
        }
    }
    
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String? = null) : ConnectionState()
    }
}

