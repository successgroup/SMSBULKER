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
    private val onSelectionChanged: (Set<Contact>) -> Unit,
    private val getSelectedContactsFromViewModel: () -> Set<Contact> // Renamed parameter for clarity
) : ListAdapter<Contact, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    // Remove the selectedContacts set from here
    // private val selectedContacts = mutableSetOf<Contact>()




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

    fun getSelectedContacts(): Set<Contact> = getSelectedContactsFromViewModel() // Use the renamed parameter

    fun selectAll() {
        currentList.forEach { contact ->
            if (!getSelectedContacts().contains(contact)) {
                onSelectionChanged(getSelectedContacts() + contact)
            }
        }
        notifyDataSetChanged()
    }

    fun deselectAll() {
        onSelectionChanged(emptySet())
        notifyDataSetChanged()
    }

    fun areAllSelected(): Boolean = getSelectedContacts().size == currentList.size

    fun toggleSelection() {
        if (areAllSelected()) {
            deselectAll()
        } else {
            selectAll()
        }
    }

    fun clearSelection() {
        onSelectionChanged(emptySet())
        notifyDataSetChanged()
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

                // Setup checkbox - use the getSelectedContacts function
                checkBox.isChecked = getSelectedContacts().contains(contact)
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    val currentSelection = getSelectedContacts().toMutableSet()
                    if (isChecked) {
                        currentSelection.add(contact)
                    } else {
                        currentSelection.remove(contact)
                    }
                    onSelectionChanged(currentSelection)
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