package com.gscube.smsbulker.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.gscube.smsbulker.R
import com.gscube.smsbulker.data.model.BulkSmsResult
import com.gscube.smsbulker.data.model.Recipient
import com.gscube.smsbulker.data.model.Contact
import com.gscube.smsbulker.databinding.FragmentHomeBinding
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.ui.auth.LoginActivity
import com.gscube.smsbulker.utils.IPermissionManager
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var viewModel: HomeViewModel
    
    @Inject
    lateinit var permissionManager: IPermissionManager
    
    private lateinit var recipientsAdapter: RecipientsAdapter
    private var progressDialog: SendingProgressDialog? = null
    private var resultsDialog: SendingResultsDialog? = null

    private val args: HomeFragmentArgs by navArgs()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // All permissions granted, proceed with action
            when (pendingAction) {
                PendingAction.LOAD_CSV -> handleLoadCsv()
                PendingAction.IMPORT_CONTACTS -> handleImportContacts()
                null -> Unit
            }
        } else {
            Snackbar.make(
                binding.root,
                "Permissions required to perform this action",
                Snackbar.LENGTH_LONG
            ).show()
        }
        pendingAction = null
    }

    private var pendingAction: PendingAction? = null

    private enum class PendingAction {
        LOAD_CSV,
        IMPORT_CONTACTS
    }
    
    private val csvFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { 
            try {
                // Take persistable permission
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.loadRecipients(uri)
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Failed to load CSV file: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as SmsBulkerApplication)
            .appComponent.inject(this)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Observe ViewModel state
        setupObservers()
        
        // Setup UI components
        setupRecyclerView()
        setupClickListeners()
        setupMessageInput()
        
        // Handle template from navigation
        args.template?.let { template ->
            viewModel.setSelectedTemplate(template)
        }
        
        // Handle selected contacts from ContactsFragment
        arguments?.getParcelableArrayList<Contact>("selected_contacts")?.let { contacts ->
            viewModel.addRecipients(contacts)
        }
    }

    private fun setupRecyclerView() {
        recipientsAdapter = RecipientsAdapter { recipient ->
            viewModel.removeRecipient(recipient)
        }
        binding.recipientsRecyclerView.apply {
            adapter = recipientsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            sendButton.setOnClickListener { validateAndSend() }
            selectTemplateButton.setOnClickListener {
                findNavController().navigate(R.id.nav_templates)
            }
            selectRecipientsButton.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_contactsFragment)
            }
            clearRecipientsButton.setOnClickListener {
                viewModel.clearRecipients()
            }
            skippedContactsButton.setOnClickListener {
                showSkippedContactsDialog()
            }
        }
    }

    private fun setupMessageInput() {
        // Set initial character count format
        binding.characterCountText.text = "0(0)"
        
        // In setupMessageInput() method, modify the TextWatcher
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(charSequence: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable?) {
                editable?.toString()?.let { text -> 
                    viewModel.updateMessageContent(text)
                    // If user is editing a template, clear the template selection
                    if (viewModel.state.value.selectedTemplate != null && 
                        text != viewModel.state.value.selectedTemplate?.content) {
                        viewModel.clearSelectedTemplate()
                    }
                    // In setupMessageInput() method (around line 181)
                  
                    // Calculate message pages (152 characters per page)
                    val charCount = text.length
                    val pageCount = if (charCount == 0) 0 else ((charCount - 1) / 152) + 1
                    // Update character and page count
                    binding.characterCountText.text = "$charCount($pageCount)"
                } ?: run {
                    binding.characterCountText.text = "0(0)"
                }
            }
        })
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.loadingIndicator.isVisible = state.isLoading
                    
                    // Update message input when template changes
                    state.selectedTemplate?.let { template ->
                        if (binding.messageInput.text.toString() != template.content) {
                            // Save cursor position
                            val cursorPosition = binding.messageInput.selectionStart
                            binding.messageInput.setText(template.content)
                            // Restore cursor position, but ensure it's within bounds
                            val newPosition = minOf(cursorPosition, template.content.length)
                            if (newPosition > 0) {
                                binding.messageInput.setSelection(newPosition)
                            }
                        }
                        binding.selectedTemplateText.text = "Selected template: ${template.title}"
                        binding.selectedTemplateText.isVisible = true
                    } ?: run {
                        // If no template is selected, show the persisted message
                        if (binding.messageInput.text.toString() != state.message) {
                            // Save cursor position
                            val cursorPosition = binding.messageInput.selectionStart
                            binding.messageInput.setText(state.message)
                            // Restore cursor position, but ensure it's within bounds
                            val newPosition = minOf(cursorPosition, state.message.length)
                            if (newPosition > 0) {
                                binding.messageInput.setSelection(newPosition)
                            }
                        }
                        binding.selectedTemplateText.isVisible = false
                    }


                    // Handle sending stages
                    state.sendingStage?.let { stage ->
                        if (stage == SendingProgressDialog.SendStage.PREPARING) {
                            showProgress()
                        }
                        progressDialog?.updateStage(stage)
                    }

                    // Handle errors
                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        if (state.needsLogin) {
                            startActivity(Intent(requireContext(), LoginActivity::class.java))
                            requireActivity().finish()
                        }
                        viewModel.clearError()
                    }

                    // Update recipients and skipped contacts
                    recipientsAdapter.submitList(state.recipients)
                    
                    // Show/hide recipients card and update count
                    binding.recipientsCard.isVisible = state.recipients.isNotEmpty()
                    binding.recipientCount.text = "${state.recipients.size} recipients"
                    
                    // Show/hide skipped contacts button
                    val hasSkippedContacts = state.skippedContacts.isNotEmpty()
                    binding.skippedContactsButton.isVisible = hasSkippedContacts
                    if (hasSkippedContacts) {
                        binding.skippedContactsButton.text = "Skipped (${state.skippedContacts.size})"
                    }

                    // Handle success messages
                    state.success?.let { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                        viewModel.clearSuccess()
                    }
                    // Update sender ID text with more prominence
                    binding.senderIdText.text = state.senderID?.let { "Sender ID: $it" } ?: ""
                    binding.senderIdText.isVisible = state.senderID != null
                }
            }
        }
    }

    private fun checkPermissionsAndLoadCsv() {
        val permissions = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            emptyArray()
        }
        
        if (permissions.isEmpty() || permissions.all { permission -> 
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED 
        }) {
            handleLoadCsv()
        } else {
            pendingAction = PendingAction.LOAD_CSV
            requestPermissions.launch(permissions)
        }
    }

    private fun checkPermissionsAndImportContacts() {
        val permissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        
        if (permissions.all { permission -> 
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED 
        }) {
            handleImportContacts()
        } else {
            pendingAction = PendingAction.IMPORT_CONTACTS
            requestPermissions.launch(permissions)
        }
    }

    private fun handleLoadCsv() {
        csvFilePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv"))
    }

    private fun handleImportContacts() {
        viewModel.importContacts()
    }

    private fun validateAndSend() {
        // First refresh the credit balance to ensure we have the latest value
        viewModel.refreshCreditBalance()
        
        // Add a small delay to ensure the balance is updated before proceeding
        lifecycleScope.launch {
            delay(300) // Short delay to allow balance update
            
            val state = viewModel.state.value
            when {
                state.recipients.isEmpty() -> {
                    showError("Please add recipients first")
                    return@launch
                }
                state.selectedTemplate == null && binding.messageInput.text.isNullOrBlank() -> {
                    showError("Please enter a message or select a template")
                    return@launch
                }
            }
        
            // Get message text
            val messageText = binding.messageInput.text.toString()
            
            // Check if message contains placeholders
            val hasPlaceholders = containsPlaceholders(messageText)
            
            // Calculate required credits based on actual message length after placeholder substitution
            var totalCredits = 0
            
            if (hasPlaceholders) {
                // For messages with placeholders, calculate credits for each recipient individually
                for (recipient in state.recipients) {
                    val personalizedMessage = replacePlaceholders(messageText, recipient)
                    val charCount = personalizedMessage.length
                    val pageCount = if (charCount == 0) 0 else ((charCount - 1) / 152) + 1
                    totalCredits += pageCount
                }
            } else {
                // For messages without placeholders, use the same length for all recipients
                val charCount = messageText.length
                val pageCount = if (charCount == 0) 0 else ((charCount - 1) / 152) + 1
                totalCredits = pageCount * state.recipients.size
            }
        
            // Check if user has enough credits
            if (totalCredits > state.availableCredits) {
                showError("Insufficient credits. You need $totalCredits credits but only have ${state.availableCredits} available.")
                return@launch
            }
        
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirm Send")
                .setMessage("Are you sure you want to send this message to ${state.recipients.size} recipients?\n\n" +
                           "This will consume $totalCredits credit(s) out of ${state.availableCredits} available." +
                           (if (hasPlaceholders) "\n\nNote: Credit calculation is based on personalized message length after placeholder replacement." else ""))
                .setPositiveButton("Send") { _, _ ->
                    viewModel.sendBulkSms()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showProgress() {
        progressDialog = SendingProgressDialog.newInstance()
        progressDialog?.show(childFragmentManager, SendingProgressDialog.TAG)
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSkippedContactsDialog() {
        val skippedContacts = viewModel.getSkippedContacts()
        
        if (skippedContacts.isEmpty()) {
            Snackbar.make(binding.root, "No skipped contacts to show", Snackbar.LENGTH_SHORT).show()
            return
        }
        
        val dialog = SkippedContactsDialog.newInstance(
            skippedContacts = skippedContacts,
            onExportClick = {
                viewModel.exportSkippedContactsToCSV()
            },
            onClearAllClick = {
                viewModel.clearSkippedContacts()
            },
            onRemoveContact = { contact ->
                viewModel.removeSkippedContact(contact)
            }
        )
        
        dialog.show(parentFragmentManager, "SkippedContactsDialog")
    }

    // Helper method to check if message contains placeholders
    private fun containsPlaceholders(message: String): Boolean {
        // Check for both {name} and <%name%> formats
        return Regex("\\{(\\w+)\\}").find(message) != null || 
               Regex("<%([\\w]+)%>").find(message) != null
    }
    
    // Helper method to replace placeholders with recipient data
    private fun replacePlaceholders(message: String, recipient: Recipient): String {
        if (recipient.variables.isNullOrEmpty()) {
            return message
        }
        
        var personalizedMessage = message
        
        // Replace {name} format
        val curlyBraceRegex = Regex("\\{(\\w+)\\}")
        personalizedMessage = personalizedMessage.replace(curlyBraceRegex) { matchResult ->
            val variableName = matchResult.groupValues[1]
            recipient.variables[variableName] ?: matchResult.value
        }
        
        // Replace <%name%> format
        val arkeselRegex = Regex("<%([\\w]+)%>")
        personalizedMessage = personalizedMessage.replace(arkeselRegex) { matchResult ->
            val variableName = matchResult.groupValues[1]
            recipient.variables[variableName] ?: matchResult.value
        }
        
        return personalizedMessage
    }
    
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_home, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                findNavController().navigate(R.id.action_homeFragment_to_settingsFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        progressDialog = null
        resultsDialog = null
    }
}