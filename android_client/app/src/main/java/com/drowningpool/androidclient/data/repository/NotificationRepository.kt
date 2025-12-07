package com.drowningpool.androidclient.data.repository

import com.drowningpool.androidclient.data.api.ApiServiceFactory
import com.drowningpool.androidclient.data.local.PendingResponseDao
import com.drowningpool.androidclient.data.local.PendingResponseEntity
import com.drowningpool.androidclient.data.websocket.WebSocketClient
import com.drowningpool.androidclient.domain.model.NotificationResponse
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val webSocketClient: WebSocketClient,
    private val apiServiceFactory: ApiServiceFactory,
    private val pendingResponseDao: PendingResponseDao
) {
    
    fun connect(serverUrl: String, clientId: String, context: android.content.Context? = null) {
        webSocketClient.connect(serverUrl, clientId)
        
        // Запускаем Foreground Service для поддержания соединения в фоне
        context?.let { ctx ->
            val intent = android.content.Intent(ctx, com.drowningpool.androidclient.data.websocket.WebSocketService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }
    }
    
    fun disconnect(context: android.content.Context? = null) {
        webSocketClient.disconnect()
        
        // Останавливаем Foreground Service
        context?.let { ctx ->
            val intent = android.content.Intent(ctx, com.drowningpool.androidclient.data.websocket.WebSocketService::class.java)
            ctx.stopService(intent)
        }
    }
    
    fun getConnectionState() = webSocketClient.connectionState
    
    fun getNotifications() = webSocketClient.notifications
    
    suspend fun sendResponse(
        serverBaseUrl: String,
        violationId: String,
        response: Boolean,
        useWebSocket: Boolean = true
    ): Result<Unit> {
        return try {
            if (useWebSocket && webSocketClient.connectionState.value is WebSocketClient.ConnectionState.Connected) {
                val notificationResponse = NotificationResponse(
                    violationId = violationId,
                    response = response
                )
                val success = webSocketClient.sendResponse(notificationResponse)
                if (success) {
                    // Удаляем из pending, если есть
                    pendingResponseDao.deleteByViolationId(violationId)
                    Result.success(Unit)
                } else {
                    // Сохраняем в pending для последующей отправки
                    savePendingResponse(violationId, response)
                    Result.failure(Exception("Failed to send via WebSocket"))
                }
            } else {
                // Fallback на HTTP
                val apiService = apiServiceFactory.create(serverBaseUrl)
                val httpResponse = apiService.sendNotificationResponse(
                    com.drowningpool.androidclient.data.api.NotificationResponseRequest(
                        violation_id = violationId,
                        response = response
                    )
                )
                if (httpResponse.isSuccessful) {
                    pendingResponseDao.deleteByViolationId(violationId)
                    Result.success(Unit)
                } else {
                    savePendingResponse(violationId, response)
                    Result.failure(Exception("HTTP response failed: ${httpResponse.code()}"))
                }
            }
        } catch (e: Exception) {
            savePendingResponse(violationId, response)
            Result.failure(e)
        }
    }
    
    fun getPendingResponses(): Flow<List<PendingResponseEntity>> {
        return pendingResponseDao.getAllPendingResponses()
    }
    
    private suspend fun savePendingResponse(violationId: String, response: Boolean) {
        val entity = PendingResponseEntity(
            violationId = violationId,
            response = response,
            timestamp = Instant.now().toString()
        )
        pendingResponseDao.insertPendingResponse(entity)
    }
}

