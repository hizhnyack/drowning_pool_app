package com.drowningpool.androidclient.presentation.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drowningpool.androidclient.databinding.ActivitySettingsBinding
import com.drowningpool.androidclient.presentation.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupObservers()
        setupClickListeners()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.serverIp.collect { ip ->
                binding.editTextServerIp.setText(ip)
            }
        }
        
        lifecycleScope.launch {
            viewModel.serverPort.collect { port ->
                binding.editTextServerPort.setText(port.toString())
            }
        }
        
        lifecycleScope.launch {
            viewModel.autoConnect.collect { enabled ->
                binding.switchAutoConnect.isChecked = enabled
            }
        }
        
        lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                updateConnectionState(state)
            }
        }
        
        lifecycleScope.launch {
            viewModel.client.collect { client ->
                if (client != null) {
                    binding.textClientId.text = client.clientId
                    binding.textDeviceId.text = client.deviceId
                    binding.cardClientInfo.visibility = android.view.View.VISIBLE
                } else {
                    binding.cardClientInfo.visibility = android.view.View.GONE
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                if (error != null) {
                    android.widget.Toast.makeText(this@SettingsActivity, error, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun updateConnectionState(state: com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState) {
        when (state) {
            is com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState.Disconnected -> {
                binding.textConnectionStatus.text = "Отключено"
                binding.textConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                binding.buttonConnect.text = "Подключиться"
                binding.buttonConnect.isEnabled = true
            }
            is com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState.Connecting -> {
                binding.textConnectionStatus.text = "Подключение..."
                binding.textConnectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
                binding.buttonConnect.text = "Подключение..."
                binding.buttonConnect.isEnabled = false
            }
            is com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState.Connected -> {
                binding.textConnectionStatus.text = "Подключено"
                binding.textConnectionStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                binding.buttonConnect.text = "Отключиться"
                binding.buttonConnect.isEnabled = true
            }
            is com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState.Error -> {
                binding.textConnectionStatus.text = "Ошибка: ${state.message}"
                binding.textConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                binding.buttonConnect.text = "Подключиться"
                binding.buttonConnect.isEnabled = true
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.buttonConnect.setOnClickListener {
            if (viewModel.connectionState.value is com.drowningpool.androidclient.data.websocket.WebSocketClient.ConnectionState.Connected) {
                viewModel.disconnect(this@SettingsActivity)
            } else {
                val ip = binding.editTextServerIp.text.toString()
                val port = binding.editTextServerPort.text.toString().toIntOrNull() ?: 8000
                viewModel.serverIp.value = ip
                viewModel.serverPort.value = port
                viewModel.connect(this@SettingsActivity)
            }
        }
        
        binding.switchAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveAutoConnect(isChecked)
        }
    }
}

