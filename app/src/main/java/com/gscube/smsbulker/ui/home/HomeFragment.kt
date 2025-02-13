package com.gscube.smsbulker.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.gscube.smsbulker.R
import com.gscube.smsbulker.data.model.BulkSmsResult
import com.gscube.smsbulker.databinding.FragmentHomeBinding
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.ui.home.HomeViewState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var viewModel: HomeViewModel
    
    private lateinit var recipientsAdapter: RecipientsAdapter
    private var progressDialog: SendingProgressDialog? = null
    private var resultsDialog: SendingResultsDialog? = null

    private val args: HomeFragmentArgs by navArgs()

    companion object {
        private const val TAG = "HomeFragment"
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
        (requireActivity().application as SmsBulkerApplication).appComponent.inject(this)
        super.onCreate(savedInstanceState)
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
        recipientsAdapter = RecipientsAdapter { recipient ->
            viewModel.getPreview(recipient)
        }
        binding.recipientsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recipientsAdapter
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            uploadCsvButton.setOnClickListener {
                csvFilePicker.launch("*/*")  // Accept all file types and validate later
            }

            selectTemplateButton.setOnClickListener {
                findNavController().navigate(R.id.nav_templates)
            }

            sendButton.setOnClickListener {
                viewModel.sendBulkSms()
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: HomeViewState) {
        // Update recipients list
        recipientsAdapter.submitList(state.recipients)
        
        // Show/hide empty state
        binding.recipientsPreviewCard.visibility = if (state.recipients.isEmpty()) View.GONE else View.VISIBLE
        binding.recipientsCountText.text = if (state.recipients.isEmpty()) {
            "No recipients loaded"
        } else {
            "${state.recipients.size} recipients loaded"
        }

        // Update template info
        binding.messageInput.setText(state.selectedTemplate?.content ?: "")
        binding.selectedTemplateText.text = state.selectedTemplate?.title ?: "No template selected"
        
        // Handle sending state
        if (state.isLoading) {
            showProgressDialog()
            progressDialog?.updateProgress(state.results.size, state.recipients.size)
            if (state.results.isNotEmpty()) {
                progressDialog?.updateStatus(state.results)
            }
        } else {
            hideProgressDialog()
            // Show results dialog if we have results and were previously sending
            if (state.results.isNotEmpty() && progressDialog != null) {
                showResultsDialog(state.results)
            }
        }

        // Handle errors
        state.error?.let { error ->
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG)
                .setAction("Dismiss") { 
                    viewModel.updateState { it.copy(error = null) }
                }
                .show()
        }

        // Update send button state
        binding.sendButton.isEnabled = state.recipients.isNotEmpty() && state.selectedTemplate != null
    }

    private fun showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = SendingProgressDialog.newInstance().also {
                it.show(childFragmentManager, SendingProgressDialog.TAG)
            }
        }
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun showResultsDialog(results: List<BulkSmsResult>) {
        resultsDialog = SendingResultsDialog.newInstance(results).also {
            it.show(childFragmentManager, TAG)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        progressDialog = null
        resultsDialog = null
    }
}