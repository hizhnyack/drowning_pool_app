package com.drowningpool.androidclient.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drowningpool.androidclient.data.repository.ViolationRepository
import com.drowningpool.androidclient.domain.model.Violation
import com.drowningpool.androidclient.domain.model.ViolationStatus
import com.drowningpool.androidclient.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val violationRepository: ViolationRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    private val _violations = MutableStateFlow<List<Violation>>(emptyList())
    val violations: StateFlow<List<Violation>> = _violations.asStateFlow()
    
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadViolations()
        // Синхронизация будет выполняться при подключении к серверу или при явном обновлении
    }
    
    private fun loadViolations() {
        viewModelScope.launch {
            violationRepository.getAllViolations().collect { violations ->
                // Убираем дубликаты по violationId (на случай если они есть)
                val uniqueViolations = violations.distinctBy { it.id }
                android.util.Log.d("MainViewModel", "Loaded ${uniqueViolations.size} violations from DB")
                uniqueViolations.forEachIndexed { index, violation ->
                    android.util.Log.d("MainViewModel", "  [$index] id=${violation.id}, zone=${violation.zoneName}, status=${violation.status}")
                }
                android.util.Log.d("MainViewModel", "Updating _violations StateFlow with ${uniqueViolations.size} items")
                _violations.value = uniqueViolations
                android.util.Log.d("MainViewModel", "_violations StateFlow updated. Current value size: ${_violations.value.size}")
                val pending = uniqueViolations.count { it.status == ViolationStatus.PENDING }
                _pendingCount.value = pending
                android.util.Log.d("MainViewModel", "Pending count: $pending, Total: ${uniqueViolations.size}")
            }
        }
    }
    
    fun refreshViolations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val serverBaseUrl = preferencesManager.getServerBaseUrl()
                if (serverBaseUrl.isNotEmpty()) {
                    android.util.Log.d("MainViewModel", "Syncing violations from server: $serverBaseUrl")
                    violationRepository.syncViolations(serverBaseUrl) // Используем полную синхронизацию
                    android.util.Log.d("MainViewModel", "Sync completed")
                } else {
                    android.util.Log.w("MainViewModel", "Cannot sync: serverBaseUrl is empty")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error syncing violations", e)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun onNotificationReceived(notification: com.drowningpool.androidclient.domain.model.Notification) {
        viewModelScope.launch {
            try {
                android.util.Log.d("MainViewModel", "Notification received: ${notification.violationId}")
                
                // Вместо сохранения в локальную БД, синхронизируемся с сервером
                // Это гарантирует, что локальная БД всегда соответствует серверу
                val serverBaseUrl = preferencesManager.getServerBaseUrl()
                if (serverBaseUrl.isNotEmpty()) {
                    android.util.Log.d("MainViewModel", "Syncing with server after notification")
                    violationRepository.syncViolations(serverBaseUrl)
                } else {
                    android.util.Log.w("MainViewModel", "Cannot sync: serverBaseUrl is empty")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error syncing after notification", e)
                e.printStackTrace()
            }
        }
    }
}

