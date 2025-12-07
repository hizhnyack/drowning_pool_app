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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViolationsHistoryViewModel @Inject constructor(
    private val violationRepository: ViolationRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    private val _violations = MutableStateFlow<List<Violation>>(emptyList())
    val violations: StateFlow<List<Violation>> = _violations.asStateFlow()
    
    private val _selectedStatus = MutableStateFlow<ViolationStatus?>(null)
    val selectedStatus: StateFlow<ViolationStatus?> = _selectedStatus.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        // Загружаем данные с учетом фильтров
        loadViolations()
        // Синхронизация будет выполняться при подключении к серверу или при явном обновлении
    }
    
    private fun loadViolations() {
        viewModelScope.launch {
            // Используем combine для объединения всех Flow и автоматического обновления при изменении фильтров
            combine(
                violationRepository.getAllViolations(),
                _selectedStatus,
                _searchQuery
            ) { allViolations, status, query ->
                // Убираем дубликаты по violationId (на случай если они есть)
                val uniqueViolations = allViolations.distinctBy { it.id }
                android.util.Log.d("ViolationsHistoryViewModel", "Filtering violations: total=${uniqueViolations.size}, status=$status, query=$query")
                
                var filtered = uniqueViolations
                
                // Применяем поиск
                if (query.isNotEmpty()) {
                    filtered = filtered.filter { 
                        it.zoneName.contains(query, ignoreCase = true) || 
                        it.zoneId.contains(query, ignoreCase = true) ||
                        it.id.contains(query, ignoreCase = true)
                    }
                }
                
                // Применяем фильтр по статусу
                if (status != null) {
                    filtered = filtered.filter { it.status == status }
                }
                
                android.util.Log.d("ViolationsHistoryViewModel", "Filtered to ${filtered.size} violations")
                filtered
            }.collect { filteredViolations ->
                android.util.Log.d("ViolationsHistoryViewModel", "Displaying ${filteredViolations.size} violations")
                filteredViolations.forEachIndexed { index, violation ->
                    android.util.Log.d("ViolationsHistoryViewModel", "  [$index] id=${violation.id}, zone=${violation.zoneName}, status=${violation.status}")
                }
                android.util.Log.d("ViolationsHistoryViewModel", "Updating _violations StateFlow with ${filteredViolations.size} items")
                _violations.value = filteredViolations
                android.util.Log.d("ViolationsHistoryViewModel", "_violations StateFlow updated. Current value size: ${_violations.value.size}")
            }
        }
    }
    
    fun setStatusFilter(status: ViolationStatus?) {
        _selectedStatus.value = status
        // Данные обновятся автоматически через combine
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        // Данные обновятся автоматически через combine
    }
    
    fun refreshViolations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val serverBaseUrl = preferencesManager.getServerBaseUrl()
                if (serverBaseUrl.isNotEmpty()) {
                    violationRepository.syncViolations(serverBaseUrl) // Используем полную синхронизацию
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

