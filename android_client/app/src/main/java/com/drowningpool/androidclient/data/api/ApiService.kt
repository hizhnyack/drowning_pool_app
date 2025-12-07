package com.drowningpool.androidclient.data.api

import com.drowningpool.androidclient.domain.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    @POST("api/register")
    suspend fun registerClient(
        @Body client: ClientRegister
    ): Response<Client>
    
    @POST("api/unregister/{client_id}")
    suspend fun unregisterClient(
        @Path("client_id") clientId: String
    ): Response<Map<String, String>>
    
    @GET("api/status")
    suspend fun getServerStatus(): Response<ServerStatus>
    
    @GET("api/violations")
    suspend fun getViolations(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 100
    ): Response<ViolationsResponse>
    
    @GET("api/violations/{violation_id}")
    suspend fun getViolation(
        @Path("violation_id") violationId: String
    ): Response<Violation>
    
    @GET("api/violations/{violation_id}/image")
    suspend fun getViolationImage(
        @Path("violation_id") violationId: String
    ): Response<okhttp3.ResponseBody>
    
    @POST("api/notifications/response")
    suspend fun sendNotificationResponse(
        @Body response: NotificationResponseRequest
    ): Response<Map<String, String>>
}

data class NotificationResponseRequest(
    val violation_id: String,
    val response: Boolean
)

