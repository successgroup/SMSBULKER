package com.gscube.smsbulker.ui.sendMessage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
                viewModel.sendMessage()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
