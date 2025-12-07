package com.drowningpool.androidclient.data.repository

import com.drowningpool.androidclient.data.api.ApiServiceFactory
import com.drowningpool.androidclient.data.local.ViolationDao
import com.drowningpool.androidclient.data.local.ViolationEntity
import com.drowningpool.androidclient.domain.model.Violation
import com.drowningpool.androidclient.domain.model.ViolationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViolationRepository @Inject constructor(
    private val apiServiceFactory: ApiServiceFactory,
    private val violationDao: ViolationDao
) {
    private var isSyncing = false // Флаг для предотвращения одновременных синхронизаций
    
    fun getAllViolations(): Flow<List<Violation>> {
        return violationDao.getAllViolations().map { entities ->
            android.util.Log.d("ViolationRepository", "getAllViolations: read ${entities.size} entities from DB")
            val violations = entities.map { it.toViolation() }
            violations.forEachIndexed { index, violation ->
                android.util.Log.d("ViolationRepository", "  [$index] id=${violation.id}, zone=${violation.zoneName}, status=${violation.status}")
            }
            violations
        }
    }
    
    fun getViolationsByStatus(status: ViolationStatus): Flow<List<Violation>> {
        return violationDao.getViolationsByStatus(status.name).map { entities ->
            entities.map { it.toViolation() }
        }
    }
    
    fun searchViolations(query: String): Flow<List<Violation>> {
        val searchQuery = "%$query%"
        return violationDao.searchViolations(searchQuery).map { entities ->
            entities.map { it.toViolation() }
        }
    }
    
    suspend fun getViolationById(violationId: String, serverBaseUrl: String? = null): Violation? {
        // Сначала пытаемся получить из локальной БД
        var violation = violationDao.getViolationById(violationId)?.toViolation()
        
        // Если не найдено в локальной БД и есть serverBaseUrl, загружаем с сервера
        if (violation == null && serverBaseUrl != null) {
            try {
                val apiService = apiServiceFactory.create(serverBaseUrl)
                val response = apiService.getViolation(violationId)
                if (response.isSuccessful) {
                    response.body()?.let { serverViolation ->
                        violation = serverViolation
                        // Сохраняем в локальную БД
                        violationDao.insertViolation(serverViolation.toEntity())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return violation
    }
    
    suspend fun insertViolation(violation: Violation) {
        violationDao.insertViolation(violation.toEntity())
    }
    
    suspend fun getAllViolationsList(): List<ViolationEntity> {
        // Вспомогательный метод для получения списка нарушений (не Flow)
        return violationDao.getAllViolationsSync()
    }
    
    suspend fun refreshViolations(serverBaseUrl: String, status: String? = null) {
        try {
            android.util.Log.d("ViolationRepository", "Refreshing violations from server: $serverBaseUrl, status: $status")
            val apiService = apiServiceFactory.create(serverBaseUrl)
            val response = apiService.getViolations(status, 1000) // Увеличиваем лимит для полной синхронизации
            if (response.isSuccessful) {
                val serverViolations = response.body()?.violations
                if (serverViolations != null) {
                    android.util.Log.d("ViolationRepository", "Received ${serverViolations.size} violations from server")
                    serverViolations.forEach { violation ->
                        android.util.Log.d("ViolationRepository", "Server violation ${violation.id}: status=${violation.status}")
                    }
                    
                    // Получаем все нарушения с сервера
                    val serverViolationIds = serverViolations.map { it.id }.toSet()
                    
                    // Получаем все локальные нарушения через отдельный запрос
                    val localList = violationDao.getAllViolationsSync()
                    android.util.Log.d("ViolationRepository", "Local violations count: ${localList.size}")
                    
                    // Удаляем нарушения, которых нет на сервере
                    val localIds = localList.map { it.violationId }.toSet()
                    val idsToDelete = localIds - serverViolationIds
                    
                    if (idsToDelete.isNotEmpty()) {
                        android.util.Log.d("ViolationRepository", "Deleting ${idsToDelete.size} violations not on server: $idsToDelete")
                    }
                    
                    idsToDelete.forEach { violationId ->
                        violationDao.deleteViolationById(violationId)
                    }
                    
                    // Обновляем/добавляем нарушения с сервера
                    val entities = serverViolations.map { it.toEntity() }
                    violationDao.insertViolations(entities)
                    android.util.Log.d("ViolationRepository", "Inserted/updated ${entities.size} violations in local DB")
                } else {
                    android.util.Log.e("ViolationRepository", "Response body is null or violations list is null")
                }
            } else {
                android.util.Log.e("ViolationRepository", "Response not successful: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            android.util.Log.e("ViolationRepository", "Error refreshing violations", e)
            e.printStackTrace()
        }
    }
    
    suspend fun syncViolations(serverBaseUrl: String) {
        // Полная синхронизация: получаем все нарушения с сервера и полностью заменяем локальную БД
        // Это гарантирует, что локальная БД всегда соответствует серверу
        
        // Предотвращаем одновременные синхронизации
        if (isSyncing) {
            android.util.Log.d("ViolationRepository", "Sync already in progress, skipping")
            return
        }
        
        isSyncing = true
        try {
            android.util.Log.d("ViolationRepository", "Starting full sync from server: $serverBaseUrl")
            val apiService = apiServiceFactory.create(serverBaseUrl)
            val response = apiService.getViolations(null, 10000) // Большой лимит для получения всех нарушений
            
            if (response.isSuccessful) {
                val serverViolations = response.body()?.violations
                if (serverViolations != null) {
                    android.util.Log.d("ViolationRepository", "Received ${serverViolations.size} violations from server")
                    
                    // ВАЖНО: Используем транзакцию для атомарной замены всех данных
                    // Это гарантирует, что Flow не увидит промежуточное состояние (пустую БД)
                    val entities = serverViolations.map { it.toEntity() }
                    android.util.Log.d("ViolationRepository", "Replacing all violations in DB with ${entities.size} items (transaction)")
                    entities.forEachIndexed { index, entity ->
                        android.util.Log.d("ViolationRepository", "  [$index] violationId=${entity.violationId}, zone=${entity.zoneName}, status=${entity.status}")
                    }
                    violationDao.replaceAllViolations(entities)
                    android.util.Log.d("ViolationRepository", "Sync completed: ${entities.size} violations inserted into local DB (transaction committed)")
                    
                    // Проверяем, что данные действительно сохранились
                    val verifyCount = violationDao.getAllViolationsSync().size
                    android.util.Log.d("ViolationRepository", "Verification: DB now contains $verifyCount violations")
                } else {
                    android.util.Log.e("ViolationRepository", "Response body is null or violations list is null")
                    // Если сервер вернул пустой список, очищаем локальную БД
                    violationDao.deleteAllViolations()
                    android.util.Log.d("ViolationRepository", "Server returned empty list, cleared local DB")
                }
            } else {
                android.util.Log.e("ViolationRepository", "Sync failed: ${response.code()} ${response.message()}")
                throw Exception("Failed to sync: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            android.util.Log.e("ViolationRepository", "Error during sync", e)
            throw e
        } finally {
            isSyncing = false
        }
    }
    
    suspend fun updateViolation(violation: Violation) {
        violationDao.insertViolation(violation.toEntity()) // Используем insert с REPLACE стратегией
    }
    
    private fun ViolationEntity.toViolation(): Violation {
        return Violation(
            id = violationId,
            zoneId = zoneId,
            zoneName = zoneName,
            detection = detection,
            imagePath = imagePath,
            timestamp = timestamp,
            status = status,
            operatorResponse = operatorResponse,
            operatorId = operatorId,
            responseTime = responseTime
        )
    }
    
    private fun Violation.toEntity(): ViolationEntity {
        return ViolationEntity(
            violationId = id,
            zoneId = zoneId,
            zoneName = zoneName,
            detection = detection,
            imagePath = imagePath,
            timestamp = timestamp,
            status = status,
            operatorResponse = operatorResponse,
            operatorId = operatorId,
            responseTime = responseTime
        )
    }
}

