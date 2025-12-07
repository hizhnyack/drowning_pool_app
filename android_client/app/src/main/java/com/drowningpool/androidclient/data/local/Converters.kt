package com.drowningpool.androidclient.data.local

import androidx.room.TypeConverter
import com.drowningpool.androidclient.domain.model.Detection
import com.drowningpool.androidclient.domain.model.ViolationStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromDetection(detection: Detection): String {
        return gson.toJson(detection)
    }
    
    @TypeConverter
    fun toDetection(detectionString: String): Detection {
        return gson.fromJson(detectionString, Detection::class.java)
    }
    
    @TypeConverter
    fun fromViolationStatus(status: ViolationStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toViolationStatus(statusString: String): ViolationStatus {
        return ViolationStatus.valueOf(statusString)
    }
}

