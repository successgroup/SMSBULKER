package com.gscube.smsbulker.ui.templates

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.databinding.ItemTemplateBinding

class TemplatesAdapter(
    private val onTemplateClick: (MessageTemplate) -> Unit,
    private val onEditClick: (MessageTemplate) -> Unit,
    private val onPreviewClick: (MessageTemplate) -> Unit
) : ListAdapter<MessageTemplate, TemplatesAdapter.TemplateViewHolder>(TemplateDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
        val binding = ItemTemplateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TemplateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TemplateViewHolder(
        private val binding: ItemTemplateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(template: MessageTemplate) {
            binding.apply {
                templateTitle.text = template.title
                templateContent.text = template.content
                
                // Clear existing chips and add new ones for each variable
                variablesChipGroup.removeAllViews()
                template.variables.forEach { variable ->
                    val chip = Chip(root.context).apply {
                        text = variable
                        isClickable = false
                    }
                    variablesChipGroup.addView(chip)
                }

                useButton.setOnClickListener { onTemplateClick(template) }
                editButton.setOnClickListener { onEditClick(template) }
                previewButton.setOnClickListener { onPreviewClick(template) }

                // Only show edit button for custom templates
                editButton.isVisible = template.isCustom
            }
        }
    }

    private class TemplateDiffCallback : DiffUtil.ItemCallback<MessageTemplate>() {
        override fun areItemsTheSame(oldItem: MessageTemplate, newItem: MessageTemplate): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MessageTemplate, newItem: MessageTemplate): Boolean {
            return oldItem == newItem
        }
    }
} 