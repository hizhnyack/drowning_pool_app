package com.drowningpool.androidclient.domain.model

import android.os.Parcelable
import com.drowningpool.androidclient.data.api.BooleanNumberDeserializer
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Violation(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("zone_id")
    val zoneId: String,
    
    @SerializedName("zone_name")
    val zoneName: String,
    
    @SerializedName("detection")
    val detection: Detection,
    
    @SerializedName("image_path")
    val imagePath: String,
    
    @SerializedName("timestamp")
    val timestamp: String,           // ISO 8601
    
    @SerializedName("status")
    val status: ViolationStatus,
    
    @SerializedName("operator_response")
    @JsonAdapter(BooleanNumberDeserializer::class)
    val operatorResponse: Boolean?,  // null если pending, принимает 0/1 или true/false
    
    @SerializedName("operator_id")
    val operatorId: String?,
    
    @SerializedName("response_time")
    val responseTime: String?        // ISO 8601
) : Parcelable

enum class ViolationStatus {
    @SerializedName("pending")
    PENDING,
    
    @SerializedName("confirmed")
    CONFIRMED,
    
    @SerializedName("false_positive")
    FALSE_POSITIVE
}

