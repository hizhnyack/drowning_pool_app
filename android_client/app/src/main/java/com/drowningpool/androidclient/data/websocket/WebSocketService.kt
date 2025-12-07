package com.drowningpool.androidclient.data.websocket

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.drowningpool.androidclient.R
import com.drowningpool.androidclient.presentation.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class WebSocketService : Service() {
    
    @Inject
    lateinit var webSocketClient: WebSocketClient
    
    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    inner class LocalBinder : Binder() {
        fun getService(): WebSocketService = this@WebSocketService
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service только поддерживает соединение, не создает его
        // Соединение уже создано через NotificationRepository.connect()
        return START_STICKY // Перезапускается при убийстве системы
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebSocket Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Поддержание WebSocket соединения"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Мониторинг активен")
            .setContentText("Подключение к серверу установлено")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DrowningPoolClient::WebSocketWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 часов
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    companion object {
        private const val CHANNEL_ID = "websocket_service_channel"
        private const val NOTIFICATION_ID = 1
        
        const val ACTION_CONNECT = "com.drowningpool.androidclient.CONNECT"
        const val ACTION_DISCONNECT = "com.drowningpool.androidclient.DISCONNECT"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_CLIENT_ID = "client_id"
    }
}

