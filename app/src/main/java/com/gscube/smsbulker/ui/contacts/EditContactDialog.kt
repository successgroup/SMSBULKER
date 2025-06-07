package com.gscube.smsbulker.ui.contacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gscube.smsbulker.R
import com.gscube.smsbulker.data.model.Contact
import com.gscube.smsbulker.databinding.DialogEditContactBinding
import com.gscube.smsbulker.databinding.ItemVariableBinding
import com.gscube.smsbulker.utils.PhoneNumberValidator

class EditContactDialog : DialogFragment() {
    private var _binding: DialogEditContactBinding? = null
    private val binding get() = _binding!!
    private var contact: Contact? = null
    private var onSave: ((Contact) -> Unit)? = null

    companion object {
        fun newInstance(contact: Contact? = null, onSave: (Contact) -> Unit): EditContactDialog {
            return EditContactDialog().apply {
                this.contact = contact
                this.onSave = onSave
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up the dialog title and buttons
        dialog?.setTitle(if (contact == null) "Add Contact" else "Edit Contact")

        // Initialize fields with contact data if editing
        contact?.let { contact ->
            binding.phoneInput.setText(contact.phoneNumber)
            binding.nameInput.setText(contact.name)
            binding.groupInput.setText(contact.group)

            // Add existing variables
            contact.variables.forEach { (key, value) ->
                addVariableItem(key, value)
            }
        }

        // Add variable button click listener
        binding.addVariableButton.setOnClickListener {
            addVariableItem()
        }

        // Set up validation
        setupValidation()
    }

    private fun setupValidation() {
        binding.phoneInput.doAfterTextChanged { text ->
            when {
                text.isNullOrBlank() -> {
                    binding.phoneInputLayout.error = "Phone number is required"
                }
                !PhoneNumberValidator.isValidGhanaNumber(text.toString()) -> {
                    binding.phoneInputLayout.error = "Invalid Ghana phone number format"
                }
                else -> {
                    binding.phoneInputLayout.error = null
                    // Optionally format the number as user types
                    val formattedNumber = PhoneNumberValidator.formatGhanaNumber(text.toString())
                    if (formattedNumber != null && formattedNumber != text.toString()) {
                        binding.phoneInput.setText(formattedNumber)
                        binding.phoneInput.setSelection(formattedNumber.length)
                    }
                }
            }
        }

        binding.nameInput.doAfterTextChanged { text ->
            binding.nameInputLayout.error = if (text.isNullOrBlank()) "Name is required" else null
        }
    }

    private fun addVariableItem(key: String = "", value: String = "") {
        val variableBinding = ItemVariableBinding.inflate(layoutInflater)
        
        variableBinding.keyInput.setText(key)
        variableBinding.valueInput.setText(value)
        
        variableBinding.removeButton.setOnClickListener {
            binding.variablesContainer.removeView(variableBinding.root)
        }

        binding.variablesContainer.addView(variableBinding.root)
    }

    private fun validateAndSave() {
        val phoneNumber = binding.phoneInput.text.toString()
        val name = binding.nameInput.text.toString()
        
        if (phoneNumber.isBlank()) {
            binding.phoneInputLayout.error = "Phone number is required"
            return
        }
        
        if (!PhoneNumberValidator.isValidGhanaNumber(phoneNumber)) {
            binding.phoneInputLayout.error = "Invalid Ghana phone number format"
            return
        }
        
        if (name.isBlank()) {
            binding.nameInputLayout.error = "Name is required"
            return
        }

        // Format the phone number before saving
        val formattedPhoneNumber = PhoneNumberValidator.formatGhanaNumber(phoneNumber) ?: phoneNumber

        // Collect variables
        val variables = mutableMapOf<String, String>()
        for (i in 0 until binding.variablesContainer.childCount) {
            val variableView = binding.variablesContainer.getChildAt(i)
            val variableBinding = ItemVariableBinding.bind(variableView)
            
            val key = variableBinding.keyInput.text.toString()
            val value = variableBinding.valueInput.text.toString()
            
            if (key.isNotBlank() && value.isNotBlank()) {
                variables[key] = value
            }
        }

        val updatedContact = Contact(
            id = contact?.id,
            phoneNumber = formattedPhoneNumber,
            name = name,
            group = binding.groupInput.text.toString(),
            variables = variables,
            createdAt = contact?.createdAt ?: System.currentTimeMillis()
        )

        onSave?.invoke(updatedContact)
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (contact == null) "Add Contact" else "Edit Contact")
            .setView(binding.root)
            .setPositiveButton("Save") { _, _ -> validateAndSave() }
            .setNegativeButton("Cancel") { _, _ -> dismiss() }
            .show()
            .apply {
                setOnDismissListener { this@EditContactDialog.dismiss() }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}