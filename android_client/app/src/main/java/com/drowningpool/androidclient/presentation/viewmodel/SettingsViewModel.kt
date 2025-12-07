package com.drowningpool.androidclient.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drowningpool.androidclient.data.repository.ClientRepository
import com.drowningpool.androidclient.data.repository.NotificationRepository
import com.drowningpool.androidclient.data.repository.ViolationRepository
import com.drowningpool.androidclient.data.websocket.WebSocketClient
import com.drowningpool.androidclient.domain.model.Client
import com.drowningpool.androidclient.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val notificationRepository: NotificationRepository,
    private val preferencesManager: PreferencesManager,
    private val violationRepository: ViolationRepository
) : ViewModel() {
    
    private val _connectionState = MutableStateFlow<com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState>(
        com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState.Disconnected
    )
    val connectionState: StateFlow<com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState> = _connectionState.asStateFlow()
    
    private val _client = MutableStateFlow<Client?>(null)
    val client: StateFlow<Client?> = _client.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    val serverIp = MutableStateFlow(preferencesManager.serverIp)
    val serverPort = MutableStateFlow(preferencesManager.serverPort)
    val autoConnect = MutableStateFlow(preferencesManager.autoConnect)
    
    init {
        loadClient()
        observeConnectionState()
    }
    
    private fun loadClient() {
        viewModelScope.launch {
            clientRepository.getClient().collect { client ->
                _client.value = client
                if (client != null) {
                    preferencesManager.clientId = client.clientId
                }
            }
        }
    }
    
    private fun observeConnectionState() {
        viewModelScope.launch {
            notificationRepository.getConnectionState().collect { state ->
                _connectionState.value = state
            }
        }
    }
    
    fun connect(context: android.content.Context) {
        viewModelScope.launch {
            _error.value = null
            _connectionState.value = com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState.Connecting
            
            val ip = serverIp.value.trim()
            val port = serverPort.value
            
            if (ip.isEmpty()) {
                _error.value = "Введите IP-адрес сервера"
                _connectionState.value = com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState.Disconnected
                return@launch
            }
            
            preferencesManager.serverIp = ip
            preferencesManager.serverPort = port
            
            val serverBaseUrl = preferencesManager.getServerBaseUrl()
            
            // Регистрируем клиента
            val registerResult = clientRepository.registerClient(serverBaseUrl, ip, port)
            
            registerResult.fold(
                onSuccess = { client ->
                    _client.value = client
                    preferencesManager.clientId = client.clientId
                    
                    // Подключаемся к WebSocket
                    notificationRepository.connect(serverBaseUrl, client.clientId, context)
                    
                    // После успешного подключения синхронизируем нарушения с сервером
                    // Это гарантирует, что локальная БД соответствует серверу
                    syncViolationsAfterConnect(serverBaseUrl)
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Ошибка подключения"
                    _connectionState.value = com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState.Disconnected
                }
            )
        }
    }
    
    fun disconnect(context: android.content.Context) {
        viewModelScope.launch {
            _client.value?.let { client ->
                val serverBaseUrl = preferencesManager.getServerBaseUrl()
                clientRepository.unregisterClient(serverBaseUrl, client.clientId)
            }
            notificationRepository.disconnect(context)
            preferencesManager.clientId = null
            _client.value = null
            _connectionState.value = com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState.Disconnected
        }
    }
    
    fun saveAutoConnect(enabled: Boolean) {
        preferencesManager.autoConnect = enabled
        autoConnect.value = enabled
    }
    
    private fun syncViolationsAfterConnect(serverBaseUrl: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsViewModel", "Syncing violations after connection")
                violationRepository.syncViolations(serverBaseUrl)
                android.util.Log.d("SettingsViewModel", "Sync completed")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error syncing violations after connect", e)
                // Не показываем ошибку пользователю, так как это фоновый процесс
            }
        }
    }
}

