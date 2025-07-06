package com.gscube.smsbulker.ui.templates.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.gscube.smsbulker.R
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory
import com.gscube.smsbulker.databinding.DialogAiTemplateGeneratorBinding
import com.gscube.smsbulker.ui.templates.ai.AITemplateViewModel
import javax.inject.Inject

class AITemplateGeneratorDialog : DialogFragment() {

    private var _binding: DialogAiTemplateGeneratorBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var viewModel: AITemplateViewModel

    private var onTemplateGeneratedListener: ((MessageTemplate) -> Unit)? = null

    companion object {
        fun newInstance(): AITemplateGeneratorDialog {
            return AITemplateGeneratorDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_SMSBulker_FullScreenDialog)
        (requireActivity().application as SmsBulkerApplication).appComponent.inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAiTemplateGeneratorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCategoryDropdown()
        setupListeners()
        observeViewModel()
    }

    private fun setupCategoryDropdown() {
        val categories = TemplateCategory.values().map { it.name }
        val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, categories)
        binding.categoryDropdown.setAdapter(adapter)
    }

    private fun setupListeners() {
        binding.cancelButton.setOnClickListener {
            viewModel.discardTemplate()
            dismiss()
        }

        binding.generateButton.setOnClickListener {
            val prompt = binding.promptInput.text.toString()
            if (prompt.isBlank()) {
                Snackbar.make(binding.root, "Please enter a prompt", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedCategory = binding.categoryDropdown.text.toString()
            val category = if (selectedCategory.isNotBlank()) {
                try {
                    TemplateCategory.valueOf(selectedCategory)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } else null

            viewModel.setPrompt(prompt)
            viewModel.setCategory(category)
            viewModel.generateTemplate()
        }

        binding.improveButton.setOnClickListener {
            viewModel.improveTemplate()
        }

        binding.saveButton.setOnClickListener {
            viewModel.saveTemplate()
            viewModel.generatedTemplate.value?.let { template ->
                onTemplateGeneratedListener?.invoke(template)
            }
            dismiss()
        }
    }
    
    fun setOnTemplateGeneratedListener(listener: (MessageTemplate) -> Unit) {
        onTemplateGeneratedListener = listener
    }
    
    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            updateLoadingState(state.isLoading)
            updateErrorState(state.error)
            state.template?.let { template ->
                updateTemplateDisplay(template)
            }
        }
    }
    
    private fun updateLoadingState(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
        binding.generateButton.isEnabled = !isLoading
        
        // Update visibility and enabled state of improve and save buttons
        val hasTemplate = viewModel.generatedTemplate.value != null
        binding.improveButton.isVisible = hasTemplate
        binding.saveButton.isVisible = hasTemplate
        binding.improveButton.isEnabled = !isLoading && hasTemplate
        binding.saveButton.isEnabled = !isLoading && hasTemplate
    }
    
    private fun updateErrorState(error: String?) {
        error?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
        }
    }
    
    private fun updateTemplateDisplay(template: MessageTemplate) {
        binding.templatePreviewCard.isVisible = true
        binding.generatedTemplateTitle.isVisible = true
        binding.templateTitleText.text = template.title
        binding.templateCategoryText.text = template.category.name
        binding.templateContentText.text = template.content
        
        // Display variables
        if (template.variables.isNotEmpty()) {
            binding.templateVariablesText.isVisible = true
            binding.templateVariablesText.text = "Variables: " + template.variables.joinToString(", ")
        } else {
            binding.templateVariablesText.isVisible = false
        }
        
        // Make improve and save buttons visible and enabled
        binding.improveButton.isVisible = true
        binding.saveButton.isVisible = true
        binding.improveButton.isEnabled = true
        binding.saveButton.isEnabled = true
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}