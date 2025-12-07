package com.drowningpool.androidclient.presentation.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.drowningpool.androidclient.databinding.ItemViolationBinding
import com.drowningpool.androidclient.domain.model.Violation
import com.drowningpool.androidclient.domain.model.ViolationStatus
import java.text.SimpleDateFormat
import java.util.*

class ViolationsAdapter(
    private val onItemClick: (Violation) -> Unit,
    private val getImageUrl: (Violation) -> String
) : ListAdapter<Violation, ViolationsAdapter.ViolationViewHolder>(ViolationDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViolationViewHolder {
        android.util.Log.d("ViolationsAdapter", "onCreateViewHolder called for viewType: $viewType")
        val binding = ItemViolationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        android.util.Log.d("ViolationsAdapter", "ViewHolder created")
        return ViolationViewHolder(binding, onItemClick, getImageUrl)
    }
    
    override fun onBindViewHolder(holder: ViolationViewHolder, position: Int) {
        val violation = getItem(position)
        android.util.Log.d("ViolationsAdapter", "Binding violation at position $position: id=${violation.id}, zone=${violation.zoneName}")
        holder.bind(violation)
    }
    
    override fun getItemCount(): Int {
        val count = super.getItemCount()
        android.util.Log.d("ViolationsAdapter", "getItemCount() called: $count")
        return count
    }
    
    override fun submitList(list: List<Violation>?, commitCallback: Runnable?) {
        android.util.Log.d("ViolationsAdapter", "submitList called with ${list?.size ?: 0} items")
        super.submitList(list, commitCallback)
    }
    
    class ViolationViewHolder(
        private val binding: ItemViolationBinding,
        private val onItemClick: (Violation) -> Unit,
        private val getImageUrl: (Violation) -> String
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(violation: Violation) {
            android.util.Log.d("ViolationsAdapter", "Binding violation: id=${violation.id}, zone=${violation.zoneName}, position=$adapterPosition")
            binding.textZoneName.text = violation.zoneName
            binding.textTimestamp.text = formatTimestamp(violation.timestamp)
            binding.textConfidence.text = "Уверенность: ${(violation.detection.confidence * 100).toInt()}%"
            binding.textStatus.text = getStatusText(violation.status)
            
            // Загрузка превью изображения
            val imageUrl = getImageUrl(violation)
            android.util.Log.d("ViolationsAdapter", "Loading image preview from: $imageUrl")
            Glide.with(binding.root.context)
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .centerCrop()
                .into(binding.imageViolation)
            
            binding.root.setOnClickListener {
                android.util.Log.d("ViolationsAdapter", "Violation clicked: id=${violation.id}")
                onItemClick(violation)
            }
        }
        
        private fun formatTimestamp(timestamp: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                val date = inputFormat.parse(timestamp)
                date?.let { outputFormat.format(it) } ?: timestamp
            } catch (e: Exception) {
                timestamp
            }
        }
        
        private fun getStatusText(status: ViolationStatus): String {
            return when (status) {
                ViolationStatus.PENDING -> "Ожидает"
                ViolationStatus.CONFIRMED -> "Подтверждено"
                ViolationStatus.FALSE_POSITIVE -> "Ложное срабатывание"
            }
        }
    }
    
    class ViolationDiffCallback : DiffUtil.ItemCallback<Violation>() {
        override fun areItemsTheSame(oldItem: Violation, newItem: Violation): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Violation, newItem: Violation): Boolean {
            return oldItem == newItem
        }
    }
}

