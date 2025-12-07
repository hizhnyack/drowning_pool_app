package com.drowningpool.androidclient.data.repository

import android.content.Context
import android.provider.Settings
import android.os.Build
import com.drowningpool.androidclient.data.api.ApiServiceFactory
import com.drowningpool.androidclient.data.local.ClientDao
import com.drowningpool.androidclient.data.local.ClientEntity
import com.drowningpool.androidclient.domain.model.Client
import com.drowningpool.androidclient.domain.model.ClientRegister
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientRepository @Inject constructor(
    private val apiServiceFactory: ApiServiceFactory,
    private val clientDao: ClientDao,
    private val context: Context
) {
    
    fun getClient(): Flow<Client?> {
        return clientDao.getClient().map { entity ->
            entity?.toClient()
        }
    }
    
    suspend fun registerClient(
        serverBaseUrl: String,
        serverIp: String,
        serverPort: Int
    ): Result<Client> {
        return try {
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            
            val registerRequest = ClientRegister(
                deviceId = deviceId,
                deviceName = deviceName,
                platform = "android"
            )
            
            val apiService = apiServiceFactory.create(serverBaseUrl)
            val response = apiService.registerClient(registerRequest)
            
            if (response.isSuccessful && response.body() != null) {
                val client = response.body()!!
                
                // Сохраняем в локальную БД
                val entity = ClientEntity(
                    clientId = client.clientId,
                    deviceId = client.deviceId,
                    deviceName = client.deviceName,
                    serverIp = serverIp,
                    serverPort = serverPort,
                    registeredAt = client.registeredAt
                )
                clientDao.insertClient(entity)
                
                Result.success(client)
            } else {
                Result.failure(Exception("Registration failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun unregisterClient(serverBaseUrl: String, clientId: String): Result<Unit> {
        return try {
            val apiService = apiServiceFactory.create(serverBaseUrl)
            val response = apiService.unregisterClient(clientId)
            if (response.isSuccessful) {
                clientDao.deleteAll()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Unregistration failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun ClientEntity.toClient(): Client {
        return Client(
            clientId = clientId,
            deviceId = deviceId,
            deviceName = deviceName,
            platform = "android",
            registeredAt = registeredAt,
            lastSeen = registeredAt // Можно обновлять при необходимости
        )
    }
}

