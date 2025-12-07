package com.drowningpool.androidclient.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.drowningpool.androidclient.R
import com.drowningpool.androidclient.presentation.ui.ViolationDetailActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(preferencesManager.notificationVibrationEnabled)
                if (preferencesManager.notificationSoundEnabled) {
                    setSound(null, null) // Используем системный звук
                } else {
                    setSound(null, null)
                    enableVibration(false)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showViolationNotification(
        violationId: String,
        zoneName: String,
        timestamp: String
    ) {
        val intent = Intent(context, ViolationDetailActivity::class.java).apply {
            putExtra("violation_id", violationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            violationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notification_title, zoneName))
            .setContentText(context.getString(R.string.notification_text, zoneName))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.notification_text, zoneName)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(if (preferencesManager.notificationVibrationEnabled) longArrayOf(0, 500, 250, 500) else null)
            .build()
        
        notificationManager.notify(violationId.hashCode(), notification)
    }
    
    companion object {
        private const val CHANNEL_ID = "violation_alerts"
    }
}

