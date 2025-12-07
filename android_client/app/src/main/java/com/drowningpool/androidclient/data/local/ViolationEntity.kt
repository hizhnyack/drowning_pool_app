package com.drowningpool.androidclient.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.drowningpool.androidclient.domain.model.Detection
import com.drowningpool.androidclient.domain.model.ViolationStatus

@Entity(tableName = "violations")
@TypeConverters(Converters::class)
data class ViolationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val violationId: String,
    val zoneId: String,
    val zoneName: String,
    val detection: Detection,
    val imagePath: String,
    val timestamp: String,
    val status: ViolationStatus,
    val operatorResponse: Boolean?,
    val operatorId: String?,
    val responseTime: String?,
    val localImagePath: String? = null
)

