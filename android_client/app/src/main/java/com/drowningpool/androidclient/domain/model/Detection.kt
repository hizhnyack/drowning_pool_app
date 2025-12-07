package com.drowningpool.androidclient.domain.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Detection(
    @SerializedName("bbox")
    val bbox: List<Int>,      // [x1, y1, x2, y2]
    
    @SerializedName("confidence")
    val confidence: Float,
    
    @SerializedName("center")
    val center: List<Int>,    // [x, y]
    
    @SerializedName("class_id")
    val classId: Int
) : Parcelable

