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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSendingProgressBinding.inflate(LayoutInflater.from(context))

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setCancelable(false)
            .create()
    }

    fun updateProgress(sent: Int, total: Int) {
        binding.progressBar.max = total
        binding.progressBar.progress = sent
        binding.progressText.text = "$sent / $total"
    }

    fun updateStatus(results: List<BulkSmsResult>) {
        val successful = results.count { SmsStatus.valueOf(it.status) == SmsStatus.SENT || SmsStatus.valueOf(it.status) == SmsStatus.DELIVERED }
        val failed = results.count { SmsStatus.valueOf(it.status) == SmsStatus.FAILED || SmsStatus.valueOf(it.status) == SmsStatus.PENDING }
        
        binding.statusText.text = "Sent: $successful | Failed: $failed"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SendingProgressDialog"

        fun newInstance() = SendingProgressDialog()
    }
}