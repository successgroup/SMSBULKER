package com.gscube.smsbulker.ui.templates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory
import com.gscube.smsbulker.databinding.FragmentTemplatesBinding
import com.gscube.smsbulker.databinding.DialogEditTemplateBinding
import com.gscube.smsbulker.ui.templates.ai.AITemplateGeneratorDialog
import javax.inject.Inject

class TemplatesFragment : Fragment() {
    private var _binding: FragmentTemplatesBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var viewModel: TemplatesViewModel
    private lateinit var templatesAdapter: TemplatesAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        (requireActivity().application as SmsBulkerApplication).appComponent.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTemplatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupTabs()
        setupFab()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        templatesAdapter = TemplatesAdapter(
            onTemplateClick = { template ->
                findNavController().navigate(
                    TemplatesFragmentDirections.actionTemplatesFragmentToHomeFragment(template)
                )
            },
            onEditClick = { template ->
                showEditTemplateDialog(template)
            },
            onPreviewClick = { template ->
                showPreviewDialog(template)
            }
        )

        binding.templatesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = templatesAdapter
        }
    }

    private fun setupTabs() {
        binding.tabLayout.apply {
            viewModel.getCategories().forEach { category ->
                addTab(newTab().apply {
                    text = category.name
                    tag = category
                })
            }

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    viewModel.loadTemplates(tab.tag as TemplateCategory)
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }

    private fun setupFab() {
        binding.addTemplateButton.setOnClickListener {
            // Show options for adding a template
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Template")
                .setItems(arrayOf("Create Manually", "AI Generator")) { _, which ->
                    when (which) {
                        0 -> showEditTemplateDialog(null)
                        1 -> showAITemplateGeneratorDialog()
                    }
                }
                .show()
        }
    }

    private fun observeViewModel() {
        viewModel.templates.observe(viewLifecycleOwner) { templates ->
            templatesAdapter.submitList(templates)
            binding.emptyView.isVisible = templates.isEmpty()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG)
                    .setAction("Retry") {
                        viewModel.selectedCategory.value?.let { category ->
                            viewModel.loadTemplates(category)
                        }
                    }
                    .show()
                viewModel.clearError()
            }
        }
    }

    private fun showEditTemplateDialog(template: MessageTemplate?) {
        val isNewTemplate = template == null
        val currentCategory = binding.tabLayout.getTabAt(
            binding.tabLayout.selectedTabPosition
        )?.tag as TemplateCategory

        val dialogBinding = DialogEditTemplateBinding.inflate(layoutInflater)
        
        // Pre-fill fields if editing existing template
        if (!isNewTemplate) {
            dialogBinding.apply {
                titleInput.setText(template?.title)
                contentInput.setText(template?.content)
                variablesInput.setText(template?.variables?.joinToString(","))
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isNewTemplate) "New Template" else "Edit Template")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { dialog, _ ->
                val title = dialogBinding.titleInput.text.toString()
                val content = dialogBinding.contentInput.text.toString()
                val variables = dialogBinding.variablesInput.text.toString()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (title.isBlank() || content.isBlank()) {
                    Snackbar.make(
                        binding.root,
                        "Title and content are required",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val newTemplate = MessageTemplate(
                    id = template?.id,
                    title = title,
                    content = content,
                    category = currentCategory,
                    variables = variables,
                    isCustom = true
                )

                viewModel.saveTemplate(newTemplate)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPreviewDialog(template: MessageTemplate) {
        PreviewTemplateDialog.newInstance(template)
            .show(childFragmentManager, "preview_dialog")
    }

    private fun showAITemplateGeneratorDialog() {
        val dialog = AITemplateGeneratorDialog.newInstance()
        dialog.setOnTemplateGeneratedListener { template ->
            viewModel.saveTemplate(template)
        }
        dialog.show(childFragmentManager, "ai_template_generator_dialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}