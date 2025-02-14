package com.gscube.smsbulker.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gscube.smsbulker.data.model.Recipient
import com.gscube.smsbulker.databinding.ItemRecipientBinding
import com.google.android.material.chip.Chip

class RecipientsAdapter : ListAdapter<Recipient, RecipientsAdapter.ViewHolder>(RecipientDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecipientBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemRecipientBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipient: Recipient) {
            binding.apply {
                phoneNumberText.text = recipient.phoneNumber
                nameText.text = recipient.name ?: "Unknown"

                // Clear existing chips
                variablesChipGroup.removeAllViews()

                // Add chips for each variable
                recipient.variables.forEach { (key, value) ->
                    val chip = Chip(root.context).apply {
                        text = "$key: $value"
                        isClickable = false
                    }
                    variablesChipGroup.addView(chip)
                }
            }
        }
    }
}

private class RecipientDiffCallback : DiffUtil.ItemCallback<Recipient>() {
    override fun areItemsTheSame(oldItem: Recipient, newItem: Recipient): Boolean {
        return oldItem.phoneNumber == newItem.phoneNumber
    }

    override fun areContentsTheSame(oldItem: Recipient, newItem: Recipient): Boolean {
        return oldItem == newItem
    }
}