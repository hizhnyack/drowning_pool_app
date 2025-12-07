package com.drowningpool.androidclient.domain.model

import com.google.gson.annotations.SerializedName

data class Client(
    @SerializedName("client_id")
    val clientId: String,
    
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("device_name")
    val deviceName: String,
    
    @SerializedName("platform")
    val platform: String,
    
    @SerializedName("registered_at")
    val registeredAt: String,  // ISO 8601
    
    @SerializedName("last_seen")
    val lastSeen: String       // ISO 8601
)

data class ClientRegister(
    @SerializedName("device_id")
    val deviceId: String,
    
    @SerializedName("device_name")
    val deviceName: String,
    
    @SerializedName("platform")
    val platform: String = "android"
)

