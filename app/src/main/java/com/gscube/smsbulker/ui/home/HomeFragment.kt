package com.gscube.smsbulker.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
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
import com.gscube.smsbulker.databinding.FragmentHomeBinding
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.utils.IPermissionManager
import kotlinx.coroutines.flow.StateFlow
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
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { 
            try {
                viewModel.loadRecipients(it)
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Failed to load CSV file: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as SmsBulkerApplication)
            .appComponent.inject(this)
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
        setupRecyclerView()
        setupClickListeners()
        observeState()
        
        // Handle template from navigation
        args.template?.let { template ->
            viewModel.setSelectedTemplate(template)
        }
    }

    private fun setupRecyclerView() {
        recipientsAdapter = RecipientsAdapter()
        binding.recipientsRecyclerView.apply {
            adapter = recipientsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            uploadCsvButton.setOnClickListener { checkPermissionsAndLoadCsv() }
            importContactsButton.setOnClickListener { checkPermissionsAndImportContacts() }
            sendButton.setOnClickListener { validateAndSend() }
            selectTemplateButton.setOnClickListener {
                findNavController().navigate(R.id.nav_templates)
            }
        }
    }

    private fun checkPermissionsAndLoadCsv() {
        val permissions = permissionManager.getRequiredPermissions()
        if (permissions.isEmpty()) {
            handleLoadCsv()
        } else {
            pendingAction = PendingAction.LOAD_CSV
            requestPermissions.launch(permissions)
        }
    }

    private fun checkPermissionsAndImportContacts() {
        val permissions = permissionManager.getRequiredPermissions()
        if (permissions.isEmpty()) {
            handleImportContacts()
        } else {
            pendingAction = PendingAction.IMPORT_CONTACTS
            requestPermissions.launch(permissions)
        }
    }

    private fun handleLoadCsv() {
        csvFilePicker.launch("text/csv")
    }

    private fun handleImportContacts() {
        viewModel.importContacts()
    }

    private fun validateAndSend() {
        val state = viewModel.state.value
        when {
            state.recipients.isEmpty() -> {
                showError("Please add recipients first")
                return
            }
            state.selectedTemplate == null && binding.messageInput.text.isNullOrBlank() -> {
                showError("Please enter a message or select a template")
                return
            }
            state.senderID.isNullOrBlank() -> {
                showError("Please set a sender ID")
                return
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Send")
            .setMessage("Are you sure you want to send this message to ${state.recipients.size} recipients?")
            .setPositiveButton("Send") { _, _ ->
                viewModel.sendBulkSms()
                showProgress()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProgress() {
        progressDialog = SendingProgressDialog().also {
            it.show(childFragmentManager, "progress")
        }
    }

    private fun hideProgress() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun showResults(results: List<BulkSmsResult>) {
        resultsDialog = SendingResultsDialog.newInstance(ArrayList(results)).also {
            it.show(childFragmentManager, "results")
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    recipientsAdapter.submitList(state.recipients)
                    
                    binding.apply {
                        recipientsCountText.text = if (state.recipients.isEmpty()) {
                            "No recipients loaded"
                        } else {
                            "${state.recipients.size} recipients loaded"
                        }
                        
                        senderIdDisplay.text = state.senderID ?: "Not set"
                        
                        state.selectedTemplate?.let { template ->
                            messageInput.setText(template.content)
                            messageInput.isEnabled = false
                            selectedTemplateText.text = template.title
                        } ?: run {
                            messageInput.isEnabled = true
                            selectedTemplateText.text = "No template selected"
                        }

                        loadingIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    }

                    when {
                        state.error != null -> {
                            hideProgress()
                            showError(state.error)
                        }
                        state.success != null -> {
                            hideProgress()
                            showResults(state.results)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        progressDialog = null
        resultsDialog = null
    }
}