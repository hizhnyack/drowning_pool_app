package com.drowningpool.androidclient.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    
    @Query("SELECT * FROM clients LIMIT 1")
    fun getClient(): Flow<ClientEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity)
    
    @Delete
    suspend fun deleteClient(client: ClientEntity)
    
    @Query("DELETE FROM clients")
    suspend fun deleteAll()
}

