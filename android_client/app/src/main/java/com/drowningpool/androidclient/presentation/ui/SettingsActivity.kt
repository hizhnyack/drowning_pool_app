package com.drowningpool.androidclient.presentation.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.drowningpool.androidclient.databinding.ActivitySettingsBinding
import com.drowningpool.androidclient.presentation.viewmodel.SettingsViewModel
import com.drowningpool.androidclient.utils.NotificationHelper
import com.drowningpool.androidclient.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    @Inject
    lateinit var notificationHelper: NotificationHelper
    
    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                preferencesManager.notificationSoundUri = uri.toString()
                updateSelectedSoundText()
                notificationHelper.updateNotificationChannel()
            } else {
                // Пользователь выбрал "Без звука" или отменил
                preferencesManager.notificationSoundUri = null
                updateSelectedSoundText()
                notificationHelper.updateNotificationChannel()
            }
        }
    }
    
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
        
        // Обновляем UI для настроек уведомлений
        binding.switchNotificationSound.isChecked = preferencesManager.notificationSoundEnabled
        updateSelectedSoundText()
        
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
        
        binding.switchNotificationSound.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.notificationSoundEnabled = isChecked
            notificationHelper.updateNotificationChannel()
        }
        
        binding.buttonSelectSound.setOnClickListener {
            openRingtonePicker()
        }
        
        binding.buttonEditZones.setOnClickListener {
            openZonesEditor()
        }
    }
    
    private fun updateSelectedSoundText() {
        val soundUri = preferencesManager.notificationSoundUri
        if (soundUri != null) {
            try {
                val ringtone = RingtoneManager.getRingtone(this, Uri.parse(soundUri))
                binding.textSelectedSound.text = ringtone?.getTitle(this) ?: getString(com.drowningpool.androidclient.R.string.default_sound)
            } catch (e: Exception) {
                binding.textSelectedSound.text = getString(com.drowningpool.androidclient.R.string.default_sound)
            }
        } else {
            binding.textSelectedSound.text = getString(com.drowningpool.androidclient.R.string.default_sound)
        }
    }
    
    private fun openRingtonePicker() {
        val currentUri = preferencesManager.notificationSoundUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Выберите звук уведомления")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        }
        ringtonePickerLauncher.launch(intent)
    }
    
    private fun openZonesEditor() {
        val serverBaseUrl = preferencesManager.getServerBaseUrl()
        if (serverBaseUrl.isEmpty() || serverBaseUrl == "http:") {
            android.widget.Toast.makeText(
                this,
                "Сначала подключитесь к серверу",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // Открываем главную страницу системы
        val systemUrl = serverBaseUrl
        android.util.Log.d("SettingsActivity", "Opening system settings: $systemUrl")
        
        try {
            val uri = Uri.parse(systemUrl)
            android.util.Log.d("SettingsActivity", "Parsed URI: $uri")
            
            // Создаем Intent с ACTION_VIEW и категорией BROWSABLE
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // Проверяем, есть ли приложение для обработки
            val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            android.util.Log.d("SettingsActivity", "ResolveInfo: $resolveInfo")
            
            if (resolveInfo != null) {
                startActivity(intent)
                android.util.Log.d("SettingsActivity", "Browser opened successfully")
            } else {
                // Пробуем альтернативный способ - показываем диалог выбора браузера
                android.util.Log.w("SettingsActivity", "No default browser found, trying chooser")
                val chooser = Intent.createChooser(intent, "Выберите браузер")
                chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                if (chooser.resolveActivity(packageManager) != null) {
                    startActivity(chooser)
                } else {
                    android.util.Log.e("SettingsActivity", "No browser available at all")
                    android.widget.Toast.makeText(
                        this,
                        "Браузер не найден. Установите браузер или откройте $systemUrl вручную",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Error opening browser", e)
            android.widget.Toast.makeText(
                this,
                "Ошибка открытия браузера: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}

