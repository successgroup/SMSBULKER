package com.gscube.smsbulker.ui.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.gscube.smsbulker.data.model.BulkSmsResult
import com.gscube.smsbulker.data.model.SmsStatus
import com.gscube.smsbulker.databinding.DialogSendingResultsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SendingResultsDialog : DialogFragment() {
    private var _binding: DialogSendingResultsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSendingResultsBinding.inflate(LayoutInflater.from(context))

        val results = requireArguments().getParcelableArrayList<BulkSmsResult>(ARG_RESULTS)
            ?: emptyList()

        val successful = results.count { SmsStatus.fromString(it.status) in listOf(SmsStatus.SENT, SmsStatus.DELIVERED) }
        val failed = results.count { SmsStatus.fromString(it.status) == SmsStatus.FAILED }
        val total = results.size

        with(binding) {
            successCountText.text = successful.toString()
            failedCountText.text = failed.toString()
            creditsText.text = "Total Messages: $total"

            // Show retry text if there are failed messages
            retryText.visibility = if (failed > 0) android.view.View.VISIBLE else android.view.View.GONE
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton("OK") { _, _ -> dismiss() }
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_RESULTS = "results"

        fun newInstance(results: List<BulkSmsResult>): SendingResultsDialog {
            return SendingResultsDialog().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_RESULTS, ArrayList(results))
                }
            }
        }
    }
}