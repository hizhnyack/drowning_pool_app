package com.drowningpool.androidclient.presentation.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.drowningpool.androidclient.databinding.ActivityViolationDetailBinding
import com.drowningpool.androidclient.domain.model.ViolationStatus
import com.drowningpool.androidclient.presentation.viewmodel.ViolationDetailViewModel
import com.drowningpool.androidclient.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ViolationDetailActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityViolationDetailBinding
    private val viewModel: ViolationDetailViewModel by viewModels()
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    private var violationId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViolationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        violationId = intent.getStringExtra("violation_id")
        
        setupToolbar()
        setupObservers()
        setupClickListeners()
        
        // Устанавливаем callback для обновления списка нарушений после отправки ответа
        viewModel.onViolationUpdated = {
            // Уведомляем MainActivity о необходимости обновления (через setResult)
            setResult(RESULT_OK)
        }
        
        violationId?.let { viewModel.loadViolation(it) }
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
            viewModel.violation.collect { violation ->
                violation?.let { updateUI(it) }
            }
        }
        
        lifecycleScope.launch {
            viewModel.responseSent.collect { response ->
                if (response != null) {
                    binding.buttonConfirm.isEnabled = false
                    binding.buttonFalsePositive.isEnabled = false
                    android.widget.Toast.makeText(
                        this@ViolationDetailActivity,
                        if (response) "Нарушение подтверждено" else "Отмечено как ложное срабатывание",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun updateUI(violation: com.drowningpool.androidclient.domain.model.Violation) {
        binding.textZoneName.text = violation.zoneName
        binding.textTimestamp.text = formatTimestamp(violation.timestamp)
        binding.textConfidence.text = "${(violation.detection.confidence * 100).toInt()}%"
        binding.textCoordinates.text = "X: ${violation.detection.center[0]}, Y: ${violation.detection.center[1]}"
        
        when (violation.status) {
            ViolationStatus.PENDING -> {
                binding.textStatus.text = "Ожидает"
                binding.buttonConfirm.visibility = android.view.View.VISIBLE
                binding.buttonFalsePositive.visibility = android.view.View.VISIBLE
            }
            ViolationStatus.CONFIRMED -> {
                binding.textStatus.text = "Подтверждено"
                binding.textStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                binding.buttonConfirm.visibility = android.view.View.GONE
                binding.buttonFalsePositive.visibility = android.view.View.GONE
            }
            ViolationStatus.FALSE_POSITIVE -> {
                binding.textStatus.text = "Ложное срабатывание"
                binding.textStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                binding.buttonConfirm.visibility = android.view.View.GONE
                binding.buttonFalsePositive.visibility = android.view.View.GONE
            }
        }
        
        // Загрузка изображения
        val imageUrl = if (violation.imagePath.startsWith("http") || violation.imagePath.startsWith("/api/")) {
            // Если это уже полный URL или путь API, используем как есть
            if (violation.imagePath.startsWith("http")) {
                violation.imagePath
            } else {
                "${preferencesManager.getServerBaseUrl()}${violation.imagePath}"
            }
        } else {
            // Если это относительный путь к файлу, используем API endpoint
            "${preferencesManager.getServerBaseUrl()}/api/violations/${violation.id}/image"
        }
        
        android.util.Log.d("ViolationDetailActivity", "Loading image from: $imageUrl")
        Glide.with(this)
            .load(imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .into(binding.imageViolation)
    }
    
    private fun setupClickListeners() {
        binding.buttonConfirm.setOnClickListener {
            violationId?.let { id ->
                viewModel.sendResponse(id, true)
            }
        }
        
        binding.buttonFalsePositive.setOnClickListener {
            violationId?.let { id ->
                viewModel.sendResponse(id, false)
            }
        }
    }
    
    private fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            date?.let { outputFormat.format(it) } ?: timestamp
        } catch (e: Exception) {
            timestamp
        }
    }
}

