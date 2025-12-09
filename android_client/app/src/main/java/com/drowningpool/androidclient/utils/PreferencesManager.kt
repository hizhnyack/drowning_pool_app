package com.drowningpool.androidclient.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "drowning_pool_prefs",
        Context.MODE_PRIVATE
    )
    
    var serverIp: String
        get() = prefs.getString("server_ip", "") ?: ""
        set(value) = prefs.edit().putString("server_ip", value).apply()
    
    var serverPort: Int
        get() = prefs.getInt("server_port", 8000)
        set(value) = prefs.edit().putInt("server_port", value).apply()
    
    var clientId: String?
        get() = prefs.getString("client_id", null)
        set(value) = prefs.edit().putString("client_id", value).apply()
    
    var autoConnect: Boolean
        get() = prefs.getBoolean("auto_connect", false)
        set(value) = prefs.edit().putBoolean("auto_connect", value).apply()
    
    var notificationSoundEnabled: Boolean
        get() = prefs.getBoolean("notification_sound_enabled", true)
        set(value) = prefs.edit().putBoolean("notification_sound_enabled", value).apply()
    
    var notificationVibrationEnabled: Boolean
        get() = prefs.getBoolean("notification_vibration_enabled", true)
        set(value) = prefs.edit().putBoolean("notification_vibration_enabled", value).apply()
    
    var notificationSoundUri: String?
        get() = prefs.getString("notification_sound_uri", null)
        set(value) = prefs.edit().putString("notification_sound_uri", value).apply()
    
    fun getServerBaseUrl(): String {
        return "http://$serverIp:$serverPort"
    }
}

