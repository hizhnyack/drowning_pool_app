package com.drowningpool.androidclient.presentation.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.drowningpool.androidclient.databinding.ActivityViolationsHistoryBinding
import com.drowningpool.androidclient.domain.model.ViolationStatus
import com.drowningpool.androidclient.presentation.adapter.ViolationsAdapter
import com.drowningpool.androidclient.presentation.viewmodel.ViolationsHistoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ViolationsHistoryActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityViolationsHistoryBinding
    private val viewModel: ViolationsHistoryViewModel by viewModels()
    private lateinit var violationsAdapter: ViolationsAdapter
    
    @Inject
    lateinit var preferencesManager: com.drowningpool.androidclient.utils.PreferencesManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViolationsHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        // Синхронизируемся с сервером при открытии экрана
        viewModel.refreshViolations()
    }
    
    override fun onResume() {
        super.onResume()
        // Синхронизируемся с сервером при возврате на экран
        viewModel.refreshViolations()
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
    
    private fun setupRecyclerView() {
        violationsAdapter = ViolationsAdapter(
            onItemClick = { violation ->
                openViolationDetail(violation.id)
            },
            getImageUrl = { violation ->
                val serverBaseUrl = preferencesManager.getServerBaseUrl()
                // Строим URL для изображения аналогично ViolationDetailActivity
                if (violation.imagePath.startsWith("http") || violation.imagePath.startsWith("/api/")) {
                    if (violation.imagePath.startsWith("http")) {
                        violation.imagePath
                    } else {
                        "$serverBaseUrl${violation.imagePath}"
                    }
                } else {
                    "$serverBaseUrl/api/violations/${violation.id}/image"
                }
            }
        )
        binding.recyclerViewViolations.apply {
            layoutManager = LinearLayoutManager(this@ViolationsHistoryActivity)
            adapter = violationsAdapter
            android.util.Log.d("ViolationsHistoryActivity", "RecyclerView setup completed. Initial adapter itemCount: ${violationsAdapter.itemCount}")
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.violations.collect { violations ->
                android.util.Log.d("ViolationsHistoryActivity", "Violations received in Activity: ${violations.size} items")
                violations.forEachIndexed { index, violation ->
                    android.util.Log.d("ViolationsHistoryActivity", "  [$index] id=${violation.id}, zone=${violation.zoneName}, status=${violation.status}")
                }
                violationsAdapter.submitList(violations) {
                    android.util.Log.d("ViolationsHistoryActivity", "Adapter submitList completed. Adapter itemCount: ${violationsAdapter.itemCount}")
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.swipeRefresh.isRefreshing = isLoading
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshViolations()
        }
        
        binding.chipAll.setOnClickListener {
            viewModel.setStatusFilter(null)
        }
        
        binding.chipPending.setOnClickListener {
            viewModel.setStatusFilter(ViolationStatus.PENDING)
        }
        
        binding.chipConfirmed.setOnClickListener {
            viewModel.setStatusFilter(ViolationStatus.CONFIRMED)
        }
        
        binding.chipFalsePositive.setOnClickListener {
            viewModel.setStatusFilter(ViolationStatus.FALSE_POSITIVE)
        }
    }
    
    private fun openViolationDetail(violationId: String) {
        val intent = Intent(this, ViolationDetailActivity::class.java).apply {
            putExtra("violation_id", violationId)
        }
        startActivity(intent)
    }
}

