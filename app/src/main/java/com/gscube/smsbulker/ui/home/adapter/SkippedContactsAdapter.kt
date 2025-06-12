package com.gscube.smsbulker.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gscube.smsbulker.data.model.SkippedContact
import com.gscube.smsbulker.data.model.SkipReason
import com.gscube.smsbulker.databinding.ItemSkippedContactBinding

class SkippedContactsAdapter(
    private val onRemoveClick: (SkippedContact) -> Unit
) : ListAdapter<SkippedContact, SkippedContactsAdapter.ViewHolder>(SkippedContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSkippedContactBinding.inflate(
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
        private val binding: ItemSkippedContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(skippedContact: SkippedContact) {
            binding.apply {
                nameText.text = skippedContact.name.ifEmpty { "Unknown" }
                phoneText.text = skippedContact.phoneNumber
                reasonText.text = skippedContact.reason.getDisplayName()
                
                removeButton.setOnClickListener {
                    onRemoveClick(skippedContact)
                }
            }
        }
    }
    
    private fun SkipReason.getDisplayName(): String {
        return when (this) {
            SkipReason.TOO_SHORT -> "Too Short"
            SkipReason.TOO_LONG -> "Too Long"
            SkipReason.UNSUPPORTED_COUNTRY_CODE -> "Unsupported Country"
            SkipReason.INVALID_NETWORK_CODE -> "Invalid Network"
            SkipReason.INVALID_FORMAT -> "Invalid Format"
            SkipReason.DUPLICATE_CONTACT -> "Duplicate Contact"
        }
    }
}

class SkippedContactDiffCallback : DiffUtil.ItemCallback<SkippedContact>() {
    override fun areItemsTheSame(oldItem: SkippedContact, newItem: SkippedContact): Boolean {
        return oldItem.phoneNumber == newItem.phoneNumber
    }

    override fun areContentsTheSame(oldItem: SkippedContact, newItem: SkippedContact): Boolean {
        return oldItem == newItem
    }
}