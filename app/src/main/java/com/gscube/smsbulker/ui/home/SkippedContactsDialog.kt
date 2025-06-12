package com.gscube.smsbulker.ui.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gscube.smsbulker.data.model.SkippedContact
import com.gscube.smsbulker.data.model.SkipReason
import com.gscube.smsbulker.databinding.DialogSkippedContactsBinding
import com.gscube.smsbulker.ui.home.adapter.SkippedContactsAdapter

class SkippedContactsDialog : DialogFragment() {
    private var _binding: DialogSkippedContactsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: SkippedContactsAdapter
    private var skippedContacts: List<SkippedContact> = emptyList()
    private var onExportClick: (() -> Unit)? = null
    private var onClearAllClick: (() -> Unit)? = null
    private var onRemoveContact: ((SkippedContact) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSkippedContactsBinding.inflate(LayoutInflater.from(context))
        
        setupRecyclerView()
        setupClickListeners()
        updateUI()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setNegativeButton("Close") { _, _ -> dismiss() }
            .create()
    }
    
    private fun setupRecyclerView() {
        adapter = SkippedContactsAdapter { contact ->
            onRemoveContact?.invoke(contact)
        }
        
        binding.skippedContactsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SkippedContactsDialog.adapter
        }
    }
    
    private fun setupClickListeners() {
        binding.exportButton.setOnClickListener {
            onExportClick?.invoke()
        }
        
        binding.clearAllButton.setOnClickListener {
            onClearAllClick?.invoke()
            dismiss()
        }
    }
    
    private fun updateUI() {
        if (skippedContacts.isEmpty()) {
            binding.skippedContactsRecyclerView.visibility = View.GONE
            binding.emptyStateText.visibility = View.VISIBLE
            binding.exportButton.isEnabled = false
            binding.clearAllButton.isEnabled = false
            binding.totalSkippedText.text = "0"
            binding.commonReasonText.text = "None"
        } else {
            binding.skippedContactsRecyclerView.visibility = View.VISIBLE
            binding.emptyStateText.visibility = View.GONE
            binding.exportButton.isEnabled = true
            binding.clearAllButton.isEnabled = true
            
            // Update statistics
            binding.totalSkippedText.text = skippedContacts.size.toString()
            
            // Find most common reason
            val mostCommonReason = skippedContacts
                .groupBy { it.reason }
                .maxByOrNull { it.value.size }
                ?.key
                ?.getDisplayName() ?: "None"
            
            binding.commonReasonText.text = mostCommonReason
            
            // Update adapter
            adapter.submitList(skippedContacts)
        }
    }
    
    private fun SkipReason.getDisplayName(): String {
        return when (this) {
            SkipReason.TOO_SHORT -> "Too Short"
            SkipReason.TOO_LONG -> "Too Long"
            SkipReason.UNSUPPORTED_COUNTRY_CODE -> "Unsupported Country"
            SkipReason.INVALID_NETWORK_CODE -> "Invalid Network"
            SkipReason.INVALID_FORMAT -> "Invalid Format"
            SkipReason.DUPLICATE_CONTACT -> "Duplicate Contact" // Add this line
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        fun newInstance(
            skippedContacts: List<SkippedContact>,
            onExportClick: () -> Unit,
            onClearAllClick: () -> Unit,
            onRemoveContact: (SkippedContact) -> Unit
        ): SkippedContactsDialog {
            return SkippedContactsDialog().apply {
                this.skippedContacts = skippedContacts
                this.onExportClick = onExportClick
                this.onClearAllClick = onClearAllClick
                this.onRemoveContact = onRemoveContact
            }
        }
    }
}