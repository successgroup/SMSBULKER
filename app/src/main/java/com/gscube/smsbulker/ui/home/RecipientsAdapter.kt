package com.gscube.smsbulker.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gscube.smsbulker.data.model.Recipient
import com.gscube.smsbulker.databinding.ItemRecipientBinding
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.gscube.smsbulker.R

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

                // Set avatar text based on name
                val displayName = recipient.name ?: "No Name"
                val initials = when {
                    displayName.contains(" ") -> {
                        // For double names, take first letter of each part
                        displayName.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercase() }
                            .joinToString("")
                    }
                    displayName.isNotEmpty() -> {
                        // For single names, take first letter
                        displayName.first().uppercase()
                    }
                    else -> "?"
                }
                avatarText.text = initials

                // Set different background colors based on the name
                val colorResId = when (Math.abs(displayName.hashCode() % 12) + 1) {
                    1 -> R.color.avatar_color_1
                    2 -> R.color.avatar_color_2
                    3 -> R.color.avatar_color_3
                    4 -> R.color.avatar_color_4
                    5 -> R.color.avatar_color_5
                    6 -> R.color.avatar_color_6
                    7 -> R.color.avatar_color_7
                    8 -> R.color.avatar_color_8
                    9 -> R.color.avatar_color_9
                    10 -> R.color.avatar_color_10
                    11 -> R.color.avatar_color_11
                    else -> R.color.avatar_color_12
                }
                avatarText.setBackgroundResource(R.drawable.circle_avatar_background)
                avatarText.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.context, colorResId)
                ))

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