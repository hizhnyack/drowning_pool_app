package com.drowningpool.androidclient.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_responses")
data class PendingResponseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val violationId: String,
    val response: Boolean,
    val timestamp: String
)

