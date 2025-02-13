package com.gscube.smsbulker.ui.analytics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gscube.smsbulker.data.FailureAnalysis
import com.gscube.smsbulker.databinding.ItemFailureAnalysisBinding

class FailureAnalysisAdapter : ListAdapter<FailureAnalysis, FailureAnalysisAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFailureAnalysisBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemFailureAnalysisBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FailureAnalysis) {
            binding.apply {
                reasonText.text = item.reason
                countText.text = item.count.toString()
                percentageText.text = String.format("%.1f%%", item.percentage * 100)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<FailureAnalysis>() {
        override fun areItemsTheSame(oldItem: FailureAnalysis, newItem: FailureAnalysis): Boolean {
            return oldItem.reason == newItem.reason
        }

        override fun areContentsTheSame(oldItem: FailureAnalysis, newItem: FailureAnalysis): Boolean {
            return oldItem == newItem
        }
    }
}