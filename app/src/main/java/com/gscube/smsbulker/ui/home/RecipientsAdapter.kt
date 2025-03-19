package com.gscube.smsbulker.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gscube.smsbulker.data.model.Recipient
import com.gscube.smsbulker.databinding.ItemRecipientBinding

class RecipientsAdapter(
    private val onRemoveClick: (Recipient) -> Unit
) : ListAdapter<Recipient, RecipientsAdapter.ViewHolder>(RecipientDiffCallback()) {

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
                nameText.text = recipient.name ?: "No Name"

                // Set up remove button click listener
                removeButton.setOnClickListener {
                    onRemoveClick(recipient)
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
}