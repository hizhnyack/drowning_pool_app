package com.drowningpool.androidclient.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val clientId: String,
    val deviceId: String,
    val deviceName: String,
    val serverIp: String,
    val serverPort: Int,
    val registeredAt: String
)

