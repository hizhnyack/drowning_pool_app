package com.drowningpool.androidclient.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingResponseDao {
    
    @Query("SELECT * FROM pending_responses ORDER BY timestamp ASC")
    fun getAllPendingResponses(): Flow<List<PendingResponseEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingResponse(response: PendingResponseEntity)
    
    @Delete
    suspend fun deletePendingResponse(response: PendingResponseEntity)
    
    @Query("DELETE FROM pending_responses WHERE violationId = :violationId")
    suspend fun deleteByViolationId(violationId: String)
}

