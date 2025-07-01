package com.gscube.smsbulker.ui.sendMessage

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.gscube.smsbulker.R
import com.gscube.smsbulker.data.model.Contact
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.databinding.FragmentSendMessageBinding
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.launch

class SendMessageFragment : Fragment() {
    private var _binding: FragmentSendMessageBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var viewModel: SendMessageViewModel
    
    private var messageProcessingDialog: AlertDialog? = null

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSendMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get pre-selected contacts from arguments
        arguments?.getParcelableArray("preSelectedContacts")?.let { contacts ->
            val selectedContacts = contacts.filterIsInstance<Contact>()
            viewModel.setPreSelectedContacts(selectedContacts)
        }

        setupViews()
        observeState()
    }

    private fun setupViews() {
        binding.apply {
            // Message input handling
            messageInput.doAfterTextChanged { text ->
                viewModel.setMessage(text?.toString() ?: "")
            }

            // Sender ID input handling
            senderIdInput.doAfterTextChanged { text ->
                viewModel.setSenderId(text?.toString() ?: "")
            }

            // Schedule button handling
            scheduleButton.setOnClickListener {
                showDateTimePicker()
            }

            // Template selection handling
            selectTemplateButton.setOnClickListener {
                findNavController().navigate(R.id.action_sendMessage_to_templates)
            }

            // Send button handling
            sendButton.setOnClickListener {
                // Show validating messages dialog before sending
                showValidatingMessagesDialog()
                
                // Add a small delay to ensure the dialog is visible
                binding.root.postDelayed({
                    viewModel.sendMessage()
                }, 300)
            }

            // Add contacts button handling
            addContactsButton.setOnClickListener {
                findNavController().navigate(R.id.action_sendMessage_to_contacts)
            }
        }

        // Handle template selection result
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<MessageTemplate>("selectedTemplate")?.observe(viewLifecycleOwner) { template ->
            viewModel.setTemplate(template)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.apply {
                        // Update loading state
                        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                        sendButton.isEnabled = !state.isLoading
                        
                        // Show or dismiss the message processing dialog based on loading state
                        if (state.isLoading) {
                            showSendingMessagesDialog()
                        } else {
                            dismissMessageProcessingDialog()
                        }

                        // Update message input
                        if (messageInput.text.toString() != state.message) {
                            messageInput.setText(state.message)
                        }

                        // Update sender ID
                        if (senderIdInput.text.toString() != state.senderId) {
                            senderIdInput.setText(state.senderId)
                        }

                        // Update template info
                        templateInfo.text = state.selectedTemplate?.let { "Template: ${it.title}" } ?: ""
                        templateInfo.visibility = if (state.selectedTemplate != null) View.VISIBLE else View.GONE

                        // Update schedule info
                        scheduleInfo.text = state.scheduleTime?.let { "Scheduled: ${dateFormat.format(Date(it))}" } ?: ""
                        scheduleInfo.visibility = if (state.scheduleTime != null) View.VISIBLE else View.GONE

                        // Update recipients count
                        recipientsCount.text = "${state.selectedContacts.size} recipients selected"

                        // Show error if any
                        state.error?.let { error ->
                            showError(error)
                        }

                        // Show success if any
                        state.success?.let { success ->
                            showSuccess(success)
                        }
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Dismiss") { }
            .show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Dismiss") { }
            .show()
    }

    private fun showDateTimePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .build()

        datePicker.addOnPositiveButtonClickListener { dateInMillis ->
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setTitleText("Select time")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = dateInMillis
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(Calendar.MINUTE, timePicker.minute)
                
                viewModel.setScheduleTime(calendar.timeInMillis)
            }

            timePicker.show(childFragmentManager, "time_picker")
        }

        datePicker.show(childFragmentManager, "date_picker")
    }

    /**
     * Shows a message processing dialog with the given title and message
     */
    private fun showMessageProcessingDialog(title: String, message: String) {
        // Dismiss any existing dialog
        dismissMessageProcessingDialog()
        
        // Inflate the dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_message_processing, null)
        
        // Set the title and message
        dialogView.findViewById<TextView>(R.id.titleText).text = title
        dialogView.findViewById<TextView>(R.id.messageText).text = message
        
        // Create and show the dialog
        messageProcessingDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
            
        messageProcessingDialog?.show()
    }
    
    /**
     * Shows a dialog for sending messages
     */
    private fun showSendingMessagesDialog() {
        showMessageProcessingDialog(
            getString(R.string.sending_messages),
            getString(R.string.sending_messages_message)
        )
    }
    
    /**
     * Shows a dialog for validating messages
     */
    private fun showValidatingMessagesDialog() {
        showMessageProcessingDialog(
            getString(R.string.validating_messages),
            getString(R.string.validating_messages_message)
        )
    }
    
    /**
     * Dismisses the message processing dialog if it's showing
     */
    private fun dismissMessageProcessingDialog() {
        messageProcessingDialog?.dismiss()
        messageProcessingDialog = null
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        dismissMessageProcessingDialog()
        _binding = null
    }
}
