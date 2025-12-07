package com.drowningpool.androidclient.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drowningpool.androidclient.data.repository.NotificationRepository
import com.drowningpool.androidclient.data.repository.ViolationRepository
import com.drowningpool.androidclient.domain.model.Violation
import com.drowningpool.androidclient.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViolationDetailViewModel @Inject constructor(
    private val violationRepository: ViolationRepository,
    private val notificationRepository: NotificationRepository,
    private val preferencesManager: com.drowningpool.androidclient.utils.PreferencesManager
) : ViewModel() {
    
    // Callback для уведомления о необходимости обновления списка нарушений
    var onViolationUpdated: (() -> Unit)? = null
    
    private val _violation = MutableStateFlow<Violation?>(null)
    val violation: StateFlow<Violation?> = _violation.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _responseSent = MutableStateFlow<Boolean?>(null)
    val responseSent: StateFlow<Boolean?> = _responseSent.asStateFlow()
    
    fun loadViolation(violationId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val serverBaseUrl = preferencesManager.getServerBaseUrl()
                _violation.value = violationRepository.getViolationById(violationId, serverBaseUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun sendResponse(violationId: String, response: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val serverBaseUrl = preferencesManager.getServerBaseUrl()
                android.util.Log.d("ViolationDetailViewModel", "Sending response for violation $violationId: $response")
                
                val result = notificationRepository.sendResponse(serverBaseUrl, violationId, response)
                result.fold(
                    onSuccess = {
                        android.util.Log.d("ViolationDetailViewModel", "Response sent successfully, reloading violation from server")
                        _responseSent.value = response
                        
                        // После отправки ответа синхронизируемся с сервером
                        // Это гарантирует, что локальная БД соответствует серверу
                        try {
                            android.util.Log.d("ViolationDetailViewModel", "Syncing violations after response")
                            violationRepository.syncViolations(serverBaseUrl)
                            
                            // Загружаем обновленное нарушение с сервера
                            val updatedViolation = violationRepository.getViolationById(violationId, serverBaseUrl)
                            if (updatedViolation != null) {
                                android.util.Log.d("ViolationDetailViewModel", "Updated violation status: ${updatedViolation.status}")
                                _violation.value = updatedViolation
                                // Уведомляем о необходимости обновления списка
                                onViolationUpdated?.invoke()
                            } else {
                                // Если не удалось загрузить с сервера, обновляем локально
                                _violation.value?.let { violation ->
                                    val updated = violation.copy(
                                        status = if (response) {
                                            com.drowningpool.androidclient.domain.model.ViolationStatus.CONFIRMED
                                        } else {
                                            com.drowningpool.androidclient.domain.model.ViolationStatus.FALSE_POSITIVE
                                        },
                                        operatorResponse = response
                                    )
                                violationRepository.updateViolation(updated)
                                _violation.value = updated
                                // Уведомляем о необходимости обновления списка
                                onViolationUpdated?.invoke()
                            }
                        }
                    } catch (e: Exception) {
                            android.util.Log.e("ViolationDetailViewModel", "Error reloading violation from server", e)
                            // Fallback: обновляем локально
                            _violation.value?.let { violation ->
                                val updated = violation.copy(
                                    status = if (response) {
                                        com.drowningpool.androidclient.domain.model.ViolationStatus.CONFIRMED
                                    } else {
                                        com.drowningpool.androidclient.domain.model.ViolationStatus.FALSE_POSITIVE
                                    },
                                    operatorResponse = response
                                )
                                    violationRepository.updateViolation(updated)
                                    _violation.value = updated
                                    // Уведомляем о необходимости обновления списка
                                    onViolationUpdated?.invoke()
                            }
                        }
                    },
                    onFailure = { exception ->
                        android.util.Log.e("ViolationDetailViewModel", "Failed to send response", exception)
                        _responseSent.value = null
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ViolationDetailViewModel", "Exception sending response", e)
                e.printStackTrace()
                _responseSent.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}

