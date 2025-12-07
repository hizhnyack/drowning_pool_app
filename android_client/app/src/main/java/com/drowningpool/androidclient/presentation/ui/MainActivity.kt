package com.drowningpool.androidclient.presentation.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.drowningpool.androidclient.databinding.ActivityMainBinding
import com.drowningpool.androidclient.domain.model.Violation
import com.drowningpool.androidclient.presentation.adapter.ViolationsAdapter
import com.drowningpool.androidclient.presentation.viewmodel.MainViewModel
import com.drowningpool.androidclient.data.repository.NotificationRepository
import com.drowningpool.androidclient.utils.NotificationHelper
import com.drowningpool.androidclient.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var violationsAdapter: ViolationsAdapter
    
    @Inject
    lateinit var notificationRepository: NotificationRepository
    
    @Inject
    lateinit var notificationHelper: NotificationHelper
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        setupNotifications()
        
        // Автоподключение при запуске
        if (preferencesManager.autoConnect && preferencesManager.clientId != null) {
            val serverBaseUrl = preferencesManager.getServerBaseUrl()
            notificationRepository.connect(serverBaseUrl, preferencesManager.clientId!!, this)
            // Синхронизируем нарушения после автоподключения
            viewModel.refreshViolations()
        } else {
            // Если не автоподключение, но есть данные о сервере, синхронизируем
            val serverBaseUrl = preferencesManager.getServerBaseUrl()
            if (serverBaseUrl.isNotEmpty() && preferencesManager.clientId != null) {
                viewModel.refreshViolations()
            }
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }
    
    private fun setupRecyclerView() {
        violationsAdapter = ViolationsAdapter(
            onItemClick = { violation ->
                openViolationDetail(violation.id)
            },
            getImageUrl = { violation ->
                // Строим URL для изображения аналогично ViolationDetailActivity
                if (violation.imagePath.startsWith("http") || violation.imagePath.startsWith("/api/")) {
                    if (violation.imagePath.startsWith("http")) {
                        violation.imagePath
                    } else {
                        "${preferencesManager.getServerBaseUrl()}${violation.imagePath}"
                    }
                } else {
                    "${preferencesManager.getServerBaseUrl()}/api/violations/${violation.id}/image"
                }
            }
        )
        binding.recyclerViewViolations.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = violationsAdapter
            android.util.Log.d("MainActivity", "RecyclerView setup completed. Initial adapter itemCount: ${violationsAdapter.itemCount}")
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.violations.collect { violations ->
                android.util.Log.d("MainActivity", "Violations received in Activity: ${violations.size} items")
                violations.forEachIndexed { index, violation ->
                    android.util.Log.d("MainActivity", "  [$index] id=${violation.id}, zone=${violation.zoneName}, status=${violation.status}")
                }
                violationsAdapter.submitList(violations) {
                    android.util.Log.d("MainActivity", "Adapter submitList completed. Adapter itemCount: ${violationsAdapter.itemCount}")
                }
            }
        }
        
        // Убрано: textPendingCount больше не существует в layout
        // TODO: Добавить header в RecyclerView для отображения статуса
        
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.swipeRefresh.isRefreshing = isLoading
            }
        }
    }
    
    private fun setupNotifications() {
        lifecycleScope.launch {
            notificationRepository.getNotifications().collect { notification ->
                notification?.let {
                    android.util.Log.d("MainActivity", "Received notification: violationId=${it.violationId}, zoneName=${it.zoneName}")
                    
                    // Синхронизируемся с сервером после получения уведомления
                    viewModel.onNotificationReceived(it)
                    
                    // Показываем системное уведомление (при нажатии на него откроется экран деталей)
                    notificationHelper.showViolationNotification(
                        it.violationId,
                        it.zoneName,
                        it.timestamp
                    )
                    
                    // Автоматически открываем экран деталей нарушения только если приложение на переднем плане
                    // Если приложение в фоне, пользователь откроет через системное уведомление
                    if (!isFinishing && !isDestroyed && hasWindowFocus()) {
                        runOnUiThread {
                            openViolationDetail(it.violationId)
                        }
                    }
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.fabRefresh.setOnClickListener {
            viewModel.refreshViolations()
        }
        
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshViolations()
        }
        
        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        binding.buttonHistory.setOnClickListener {
            startActivity(Intent(this, ViolationsHistoryActivity::class.java))
        }
    }
    
    private fun openViolationDetail(violationId: String) {
        val intent = Intent(this, ViolationDetailActivity::class.java).apply {
            putExtra("violation_id", violationId)
        }
        // Используем startActivityForResult для обновления списка после возврата
        startActivity(intent)
    }
    
    override fun onResume() {
        super.onResume()
        // Обновляем список нарушений при возврате на экран (только если есть подключение)
        if (preferencesManager.clientId != null && preferencesManager.getServerBaseUrl().isNotEmpty()) {
            viewModel.refreshViolations()
        }
    }
}
