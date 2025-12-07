package com.drowningpool.androidclient.domain.model

import com.google.gson.annotations.SerializedName

data class Notification(
    @SerializedName("type")
    val type: String,         // "violation"
    
    @SerializedName("violation_id")
    val violationId: String,
    
    @SerializedName("timestamp")
    val timestamp: String,    // ISO 8601
    
    @SerializedName("zone_id")
    val zoneId: String,
    
    @SerializedName("zone_name")
    val zoneName: String,
    
    @SerializedName("detection")
    val detection: Detection,
    
    @SerializedName("image")
    val image: String?,       // base64 или null
    
    @SerializedName("image_url")
    val imageUrl: String
)

data class NotificationResponse(
    @SerializedName("type")
    val type: String = "response",         // "response"
    
    @SerializedName("violation_id")
    val violationId: String,
    
    @SerializedName("response")
    val response: Boolean     // true - подтверждено, false - ложное срабатывание
)

data class ServerStatus(
    @SerializedName("status")
    val status: String,
    
    @SerializedName("timestamp")
    val timestamp: String,
    
    @SerializedName("version")
    val version: String
)

data class ViolationsResponse(
    @SerializedName("violations")
    val violations: List<Violation>,
    
    @SerializedName("total")
    val total: Int
)

