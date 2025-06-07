package com.gscube.smsbulker.ui.templates

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.databinding.DialogPreviewTemplateBinding
import javax.inject.Inject

data class PreviewResult(
    val content: String,
    val charCount: Int,
    val messageParts: Int
)

class PreviewTemplateDialog : DialogFragment() {
    private val TAG = "PreviewTemplateDialog"
    
    @Inject
    lateinit var viewModel: TemplatesViewModel

    private var _binding: DialogPreviewTemplateBinding? = null
    private val binding get() = _binding!!
    private lateinit var template: MessageTemplate
    private val variableInputs = mutableMapOf<String, TextInputEditText>()
    private var currentPreview: PreviewResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        (requireActivity().application as SmsBulkerApplication).appComponent.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogPreviewTemplateBinding.inflate(LayoutInflater.from(requireContext()))
        template = arguments?.getParcelable("template")
            ?: throw IllegalArgumentException("Template is required")

        setupViews()
        setupButtons()
        updatePreview() // Initial preview

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Preview Template")
            .setView(binding.root)
            .setPositiveButton("Close", null)
            .create()
    }

    private fun setupViews() {
        // Add input fields for each variable
        template.variables.forEach { variable ->
            val inputLayout = TextInputLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
                hint = variable
            }

            val input = TextInputEditText(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        Log.d(TAG, "Text changed for $variable: ${s?.toString()}")
                        updatePreview()
                    }
                })
            }

            inputLayout.addView(input)
            binding.variablesContainer.addView(inputLayout)
            variableInputs[variable] = input
        }
    }

    private fun setupButtons() {
        binding.copyButton.setOnClickListener {
            currentPreview?.content?.let { text ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Message Preview", text)
                clipboard.setPrimaryClip(clip)
                Snackbar.make(binding.root, "Message copied to clipboard", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.useTemplateButton.setOnClickListener {
            findNavController().navigate(
                TemplatesFragmentDirections.actionTemplatesFragmentToHomeFragment(template)
            )
            dismiss()
        }
    }

    private fun updatePreview() {
        Log.d(TAG, "Updating preview")
        val variableValues = variableInputs.mapValues { (variable, input) ->
            val value = input.text?.toString() ?: ""
            Log.d(TAG, "Variable $variable = $value")
            value
        }
        
        val preview = preview(template, variableValues)
        currentPreview = preview
        
        binding.apply {
            previewText.text = preview.content
            
            // To:
            charCountText.text = "Characters: ${preview.charCount}/152"
            messagePartsText.text = "Message Credit(s): ${preview.messageParts}"
        }
        
        Log.d(TAG, "Preview updated: ${preview.content}")
    }

    private fun preview(template: MessageTemplate, variables: Map<String, String>): PreviewResult {
        var content = template.content
        variables.forEach { (variable, value) ->
            content = content.replace("{$variable}", value)
        }

        val charCount = content.length
        
        // To:
        val messageParts = (charCount + 151) / 152 // Round up division

        return PreviewResult(
            content = content,
            charCount = charCount,
            messageParts = messageParts
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(template: MessageTemplate): PreviewTemplateDialog {
            return PreviewTemplateDialog().apply {
                arguments = Bundle().apply {
                    putParcelable("template", template)
                }
            }
        }
    }
}