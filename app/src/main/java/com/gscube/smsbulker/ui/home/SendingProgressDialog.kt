package com.gscube.smsbulker.ui.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.gscube.smsbulker.data.model.BulkSmsResult
import com.gscube.smsbulker.data.model.SmsStatus
import com.gscube.smsbulker.databinding.DialogSendingProgressBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SendingProgressDialog : DialogFragment() {
    private var _binding: DialogSendingProgressBinding? = null
    private val binding get() = _binding!!
    private var pendingStage: SendStage? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSendingProgressBinding.inflate(LayoutInflater.from(context))

        // Set initial state
        binding.progressBar.max = 100
        binding.progressBar.progress = 0
        binding.progressText.text = "Preparing to send..."
        binding.statusText.text = ""

        // Apply any pending stage update
        pendingStage?.let { updateStage(it) }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setCancelable(false)
            .create()
    }

    fun updateStage(stage: SendStage) {
        // If binding is not ready, save the stage and apply it when dialog is created
        if (_binding == null) {
            pendingStage = stage
            return
        }

        when (stage) {
            SendStage.PREPARING -> {
                binding.progressBar.progress = 0
                binding.progressText.text = "Preparing message batch..."
                binding.statusText.text = ""
            }
            SendStage.SENDING -> {
                binding.progressBar.progress = 50
                binding.progressText.text = "Sending messages..."
                binding.statusText.text = "Please wait while we process your request"
            }
            SendStage.PROCESSING -> {
                binding.progressBar.progress = 75
                binding.progressText.text = "Processing responses..."
                binding.statusText.text = "Almost done"
            }
            SendStage.COMPLETED -> {
                binding.progressBar.progress = 100
                binding.progressText.text = "Completed"
                binding.statusText.text = "Messages sent successfully"
                dismiss()
            }
            SendStage.ERROR -> {
                binding.progressText.text = "Error occurred"
                binding.statusText.text = "Tap anywhere to dismiss"
                dialog?.setCancelable(true)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SendingProgressDialog"
        fun newInstance() = SendingProgressDialog()
    }

    enum class SendStage {
        PREPARING,    // Initial stage
        SENDING,      // API call is being made
        PROCESSING,   // Processing API response
        COMPLETED,    // All done successfully
        ERROR        // Error occurred
    }
}