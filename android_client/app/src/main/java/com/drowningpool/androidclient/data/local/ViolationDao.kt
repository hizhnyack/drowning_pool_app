package com.drowningpool.androidclient.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ViolationDao {
    
    @Query("SELECT * FROM violations ORDER BY timestamp DESC")
    fun getAllViolations(): Flow<List<ViolationEntity>>
    
    @Query("SELECT * FROM violations ORDER BY timestamp DESC")
    suspend fun getAllViolationsSync(): List<ViolationEntity>
    
    @Query("SELECT * FROM violations WHERE violationId = :violationId LIMIT 1")
    suspend fun getViolationById(violationId: String): ViolationEntity?
    
    @Query("SELECT * FROM violations WHERE status = :status ORDER BY timestamp DESC")
    fun getViolationsByStatus(status: String): Flow<List<ViolationEntity>>
    
    @Query("SELECT * FROM violations WHERE zoneName LIKE :query OR zoneId LIKE :query ORDER BY timestamp DESC")
    fun searchViolations(query: String): Flow<List<ViolationEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertViolation(violation: ViolationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertViolations(violations: List<ViolationEntity>)
    
    @Update
    suspend fun updateViolation(violation: ViolationEntity)
    
    @Delete
    suspend fun deleteViolation(violation: ViolationEntity)
    
    @Query("DELETE FROM violations WHERE violationId = :violationId")
    suspend fun deleteViolationById(violationId: String): Unit
    
    @Query("DELETE FROM violations")
    suspend fun deleteAllViolations()
    
    @Transaction
    suspend fun replaceAllViolations(violations: List<ViolationEntity>) {
        deleteAllViolations()
        insertViolations(violations)
    }
    
    @Query("DELETE FROM violations WHERE timestamp < :cutoffTime")
    suspend fun deleteOldViolations(cutoffTime: String)
}

