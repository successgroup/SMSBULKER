package com.gscube.smsbulker.ui.contacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.gscube.smsbulker.R
import com.gscube.smsbulker.data.model.Contact
import com.gscube.smsbulker.databinding.ItemContactBinding

class ContactsAdapter(
    private val onEditClick: (Contact) -> Unit,
    private val onDeleteClick: (Contact) -> Unit,
    private val onSendClick: (Contact) -> Unit,
    private val onSelectionChanged: (Set<Contact>) -> Unit
) : ListAdapter<Contact, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    private val selectedContacts = mutableSetOf<Contact>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getSelectedContacts(): Set<Contact> = selectedContacts.toSet()

    fun selectAll() {
        selectedContacts.clear()
        selectedContacts.addAll(currentList)
        notifyDataSetChanged()
        onSelectionChanged(selectedContacts)
    }

    fun deselectAll() {
        selectedContacts.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectedContacts)
    }

    fun toggleSelection() {
        if (selectedContacts.size == currentList.size) {
            deselectAll()
        } else {
            selectAll()
        }
    }

    fun clearSelection() {
        selectedContacts.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectedContacts)
    }

    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.apply {
                nameText.text = contact.name
                phoneText.text = contact.phoneNumber
                
                // Show group if present
                if (contact.group.isNotBlank()) {
                    groupText.visibility = View.VISIBLE
                    groupText.text = contact.group
                } else {
                    groupText.visibility = View.GONE
                }

                // Create chips for variables
                variablesChipGroup.removeAllViews()
                contact.variables.forEach { (key, value) ->
                    val chip = Chip(root.context).apply {
                        text = "$key: $value"
                        isClickable = false
                    }
                    variablesChipGroup.addView(chip)
                }

                // Setup checkbox
                checkBox.isChecked = selectedContacts.contains(contact)
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedContacts.add(contact)
                    } else {
                        selectedContacts.remove(contact)
                    }
                    onSelectionChanged(selectedContacts)
                }

                // Setup menu button
                menuButton.setOnClickListener { view ->
                    showPopupMenu(view, contact)
                }

                // Make the whole item clickable for selection
                root.setOnClickListener {
                    checkBox.isChecked = !checkBox.isChecked
                }
            }
        }

        private fun showPopupMenu(view: View, contact: Contact) {
            PopupMenu(view.context, view).apply {
                menuInflater.inflate(R.menu.menu_contact_item, menu)
                
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit -> {
                            onEditClick(contact)
                            true
                        }
                        R.id.action_delete -> {
                            onDeleteClick(contact)
                            true
                        }
                        R.id.action_send -> {
                            onSendClick(contact)
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }
    }
}

private class ContactDiffCallback : DiffUtil.ItemCallback<Contact>() {
    override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
        return oldItem == newItem
    }
}