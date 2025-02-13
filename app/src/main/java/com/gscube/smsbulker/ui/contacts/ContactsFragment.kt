package com.gscube.smsbulker.ui.contacts

import android.Manifest
import android.app.Activity
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.gscube.smsbulker.R
import com.gscube.smsbulker.data.model.Contact
import com.gscube.smsbulker.databinding.FragmentContactsBinding
import com.gscube.smsbulker.databinding.DialogEditContactBinding
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.ui.contacts.ContactsAdapter
import com.gscube.smsbulker.ui.contacts.ContactsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class ContactsFragment : Fragment() {

    @Inject
    lateinit var viewModel: ContactsViewModel

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!
    private lateinit var contactsAdapter: ContactsAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.WRITE_CONTACTS] == true -> {
                // Permission granted, proceed with export
                exportSelectedContactsToPhone()
            }
            permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true -> {
                // Permission granted, proceed with CSV export
                exportContactsToCSV()
            }
            else -> {
                // Show settings dialog if permission denied
                showPermissionSettingsDialog()
            }
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                navigateToCsvEditor(uri)
            }
        }
    }

    private val getContentForDirectImport = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importContacts(uri)
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
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        setupRecyclerView()
        setupSearch()
        observeState()
        observeEvents()
    }

    private fun setupViews() {
        binding.apply {
            // Setup buttons
            addContactButton.setOnClickListener {
                showEditContactDialog(null)
            }
            importButton.setOnClickListener {
                showImportDialog()
            }
            exportButton.setOnClickListener {
                requestRequiredPermissions(false)
            }
        }
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter(
            onEditClick = { contact ->
                showEditContactDialog(contact)
            },
            onDeleteClick = { contact ->
                showDeleteConfirmationDialog(contact)
            },
            onSendClick = { contact ->
                // Navigate to send message screen with single contact
                findNavController().navigate(R.id.action_contacts_to_sendMessage, bundleOf(
                    "preSelectedContacts" to arrayOf(contact)
                ))
            },
            onSelectionChanged = { selectedContacts ->
                // Show/hide send FAB based on selection
                binding.sendSelectedFab.apply {
                    visibility = if (selectedContacts.isNotEmpty()) View.VISIBLE else View.GONE
                    text = "Send to ${selectedContacts.size} Selected"
                }
            }
        )

        binding.apply {
            contactsRecyclerView.adapter = contactsAdapter
            contactsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            contactsRecyclerView.addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
            )

            // Setup send selected FAB
            sendSelectedFab.setOnClickListener {
                val selectedContacts = contactsAdapter.getSelectedContacts()
                if (selectedContacts.isNotEmpty()) {
                    findNavController().navigate(R.id.action_contacts_to_sendMessage, bundleOf(
                        "preSelectedContacts" to selectedContacts.toTypedArray()
                    ))
                    contactsAdapter.clearSelection()
                }
            }
        }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener { text ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    contactsAdapter.submitList(state.contacts)
                    binding.apply {
                        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                        emptyView.visibility = if (!state.isLoading && state.contacts.isEmpty()) View.VISIBLE else View.GONE
                        contactsRecyclerView.visibility = if (!state.isLoading && state.contacts.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        is ContactsEvent.ShowError -> showSnackbar(event.message)
                        is ContactsEvent.ShowSuccess -> showSnackbar(event.message)
                        ContactsEvent.DismissMessage -> { /* No-op */ }
                    }
                }
            }
        }
    }

    private fun showImportDialog() {
        val options = arrayOf(
            "Import CSV file directly",
            "Edit CSV file before import",
            "Import from device contacts"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose Import Method")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchFilePicker(false)
                    1 -> launchFilePicker(true)
                    2 -> requestRequiredPermissions(true)
                }
            }
            .show()
    }

    private fun launchFilePicker(openInEditor: Boolean) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            // Accept any file type but add CSV-specific MIME types
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/csv",
                "text/comma-separated-values",
                "application/csv",
                "text/plain"
            ))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        if (openInEditor) {
            getContent.launch(intent)
        } else {
            getContentForDirectImport.launch(intent)
        }
    }

    private fun exportSelectedContactsToPhone() {
        val contentResolver: ContentResolver = requireContext().contentResolver
        val contacts = viewModel.uiState.value.contacts

        try {
            contacts.forEach { contact ->
                val operations = ArrayList<ContentProviderOperation>()
                
                // Start a new raw contact
                operations.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build())

                // Add name
                operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                    .build())

                // Add phone number
                operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phoneNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build())

                contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            }
            
            Snackbar.make(binding.root, "Contacts exported successfully", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Failed to export contacts: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun exportContactsToCSV() {
        try {
            val fileName = "contacts_export_${System.currentTimeMillis()}.csv"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            
            file.bufferedWriter().use { writer ->
                // Write header
                writer.write("Name,Phone Number,Group,Variables\n")
                
                // Write contacts
                viewModel.uiState.value.contacts.forEach { contact ->
                    val variables = contact.variables.entries.joinToString(";") { "${it.key}=${it.value}" }
                    writer.write("${contact.name},${contact.phoneNumber},${contact.group},${variables}\n")
                }
            }
            
            // Notify media scanner
            val uri = Uri.fromFile(file)
            requireContext().sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
            
            Snackbar.make(binding.root, "Contacts exported to ${file.path}", Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Failed to export contacts: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showPermissionSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission Required")
            .setMessage("This feature requires additional permissions. Please grant them in Settings.")
            .setPositiveButton("Settings") { _, _ ->
                // Open app settings
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestRequiredPermissions(forExport: Boolean) {
        val permission = if (forExport) {
            Manifest.permission.WRITE_CONTACTS
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                null // No permission needed for Android 10+
            } else {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }

        permission?.let {
            requestPermissionLauncher.launch(arrayOf(it))
        } ?: run {
            // No permission needed, proceed with operation
            if (forExport) {
                exportSelectedContactsToPhone()
            } else {
                exportContactsToCSV()
            }
        }
    }

    private fun showEditContactDialog(contact: Contact?) {
        val dialogBinding = DialogEditContactBinding.inflate(layoutInflater)
        val isNewContact = contact == null
        
        if (!isNewContact) {
            dialogBinding.apply {
                phoneInput.setText(contact?.phoneNumber)
                nameInput.setText(contact?.name)
                groupInput.setText(contact?.group)
                // TODO: Handle custom variables
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isNewContact) "Add Contact" else "Edit Contact")
            .setView(dialogBinding.root)
            .setPositiveButton(if (isNewContact) "Add" else "Save") { _, _ ->
                val phone = dialogBinding.phoneInput.text?.toString()
                val name = dialogBinding.nameInput.text?.toString()
                val group = dialogBinding.groupInput.text?.toString()

                if (phone.isNullOrBlank() || name.isNullOrBlank()) {
                    showSnackbar("Phone and name are required")
                    return@setPositiveButton
                }

                val newContact = Contact(
                    id = contact?.id,
                    phoneNumber = phone,
                    name = name,
                    group = group ?: "",
                    variables = contact?.variables ?: emptyMap()
                )

                viewModel.saveContact(newContact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(contact: Contact) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete ${contact.name}?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteContact(contact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import_csv -> {
                showImportDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun navigateToCsvEditor(uri: Uri) {
        val action = ContactsFragmentDirections.actionContactsToCsvEditor(uri)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}