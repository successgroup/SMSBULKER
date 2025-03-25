package com.gscube.smsbulker.ui.contacts

import android.Manifest
import android.app.Activity
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Settings
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.gscube.smsbulker.R
import com.gscube.smsbulker.data.model.Contact
import com.gscube.smsbulker.databinding.FragmentContactsBinding
import com.gscube.smsbulker.databinding.FragmentEditContactBinding
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.ui.csvEditor.CsvEditorFragmentArgs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
                exportSelectedContactsToPhone(contactsAdapter.getSelectedContacts().toList())
            }
            permissions[Manifest.permission.READ_CONTACTS] == true -> {
                // Permission granted, proceed with import
                viewModel.importFromPhoneContacts()
            }
            permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true -> {
                // Permission granted, proceed with CSV export
                exportContactsToCSV(contactsAdapter.getSelectedContacts().toList())
            }
            else -> {
                // Show settings dialog if permission denied
                showPermissionSettingsDialog()
            }
        }
    }

    private val csvFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                // Take persistable permission
                requireContext().contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.importContactsFromCsv(selectedUri)
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Failed to import CSV: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private val csvEditorFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                // Take persistable permission
                requireContext().contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val args = bundleOf("csvUri" to selectedUri)
                findNavController().navigate(R.id.action_global_to_csvEditor, args)
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Failed to open CSV: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private val csvSaveFilePicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                viewModel.exportContactsToCSV(selectedUri, contactsAdapter.getSelectedContacts().toList())
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Failed to export CSV: ${e.message}", Snackbar.LENGTH_LONG).show()
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
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)

        setupRecyclerView()
        setupSearch()
        observeViewModel()
        setupButtons()

        return binding.root
    }

    private fun setupButtons() {
        binding.addContactButton.setOnClickListener {
            showEditContactDialog(null)
        }
        binding.importButton.setOnClickListener {
            showImportDialog()
        }
        binding.exportButton.setOnClickListener {
            showExportDialog(viewModel.uiState.value.contacts)
        }
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter(
            onEditClick = { contact ->
                showEditContactDialog(contact)
            },
            onDeleteClick = { contact ->
                showDeleteConfirmationDialog(listOf(contact))
            },
            onSendClick = { contact ->
                navigateToSendMessage(listOf(contact))
            },
            onSelectionChanged = { selectedContacts ->
                updateToolbarForSelection(selectedContacts)
            }
        )

        binding.contactsRecyclerView.apply {
            adapter = contactsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener { text ->
            val query = text?.toString() ?: ""
            viewModel.updateSearchQuery(query)
            // Don't clear selection when search is cleared
            if (query.isNotEmpty()) {
                viewModel.filterContacts(query)
            }
        }
    }

    private fun observeViewModel() {
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

    private fun updateToolbarForSelection(selectedContacts: Set<Contact>) {
        activity?.invalidateOptionsMenu()
        if (selectedContacts.isNotEmpty()) {
            binding.apply {
                sendSelectedFab.apply {
                    visibility = View.VISIBLE
                    text = "Send to ${selectedContacts.size} Selected"
                    setOnClickListener {
                        navigateToSendMessage(selectedContacts.toList())
                        contactsAdapter.clearSelection()
                    }
                }
            }
        } else {
            binding.sendSelectedFab.visibility = View.GONE
        }
    }

    private fun showSelectionActions(show: Boolean) {
        activity?.invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_contacts, menu)
        
        // Show/hide selection actions based on selection state
        val hasSelection = contactsAdapter.getSelectedContacts().isNotEmpty()
        val selectAllItem = menu.findItem(R.id.action_select_all)
        selectAllItem?.isChecked = hasSelection && contactsAdapter.areAllSelected()
        
        menu.findItem(R.id.action_delete_selected)?.isVisible = hasSelection
        menu.findItem(R.id.action_send_selected)?.isVisible = hasSelection
        menu.findItem(R.id.action_export_selected)?.isVisible = hasSelection
        
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select_all -> {
                item.isChecked = !item.isChecked
                contactsAdapter.toggleSelection()
                true
            }
            R.id.action_delete_selected -> {
                showDeleteConfirmationDialog(contactsAdapter.getSelectedContacts().toList())
                true
            }
            R.id.action_send_selected -> {
                navigateToSendMessage(contactsAdapter.getSelectedContacts().toList())
                true
            }
            R.id.action_export_selected -> {
                showExportDialog(contactsAdapter.getSelectedContacts().toList())
                true
            }
            R.id.action_import -> {
                showImportDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showImportDialog() {
        val options = arrayOf("Import from CSV", "Import from Phone", "Edit CSV before Import")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Contacts")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkPermissionsAndImportCsv()
                    1 -> checkContactsPermissionAndImport()
                    2 -> csvEditorFilePicker.launch(arrayOf("text/csv", "text/comma-separated-values"))
                }
            }
            .show()
    }

    private fun showDeleteConfirmationDialog(contacts: List<Contact>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Contacts")
            .setMessage("Are you sure you want to delete ${contacts.size} contact(s)?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteContacts(contacts)
                contactsAdapter.clearSelection()
                Snackbar.make(binding.root, "${contacts.size} contact(s) deleted", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToSendMessage(contacts: List<Contact>) {
        findNavController().navigate(
            R.id.nav_home,
            bundleOf("selected_contacts" to ArrayList(contacts))
        )
        contactsAdapter.clearSelection()
    }

    private fun showExportDialog(contacts: List<Contact>) {
        val options = arrayOf("Export to CSV", "Export to Phone Contacts")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Export Contacts")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportContactsToCSV(contacts)
                    1 -> exportContactsToPhone(contacts)
                }
            }
            .show()
    }

    private fun exportContactsToCSV(contacts: List<Contact>) {
        if (contacts.isEmpty()) {
            Snackbar.make(binding.root, "No contacts selected to export", Snackbar.LENGTH_SHORT).show()
            return
        }
        // Launch file picker with suggested name
        val fileName = "contacts_export_${System.currentTimeMillis()}.csv"
        csvSaveFilePicker.launch(fileName)
    }

    private fun exportContactsToPhone(contacts: List<Contact>) {
        if (contacts.isEmpty()) {
            Snackbar.make(binding.root, "No contacts selected to export", Snackbar.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.WRITE_CONTACTS))
        } else {
            exportSelectedContactsToPhone(contacts)
        }
    }

    private fun exportSelectedContactsToPhone(contacts: List<Contact>) {
        val contentResolver: ContentResolver = requireContext().contentResolver

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

    private fun checkPermissionsAndImportCsv() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            csvFilePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv"))
        } else {
            val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (permissions.all { permission ->
                    requireContext().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                }) {
                csvFilePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv"))
            } else {
                requestPermissionLauncher.launch(permissions)
            }
        }
    }

    private fun checkContactsPermissionAndImport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        } else {
            viewModel.importFromPhoneContacts()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_CSV && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                viewModel.importContacts(uri)
            }
        }
    }

    private fun showPermissionSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission Required")
            .setMessage("This feature requires permission to access your contacts. Please grant the permission in Settings.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditContactDialog(contact: Contact?) {
        val dialogBinding = FragmentEditContactBinding.inflate(layoutInflater)
        
        // Pre-fill fields if editing existing contact
        contact?.let { existingContact ->
            dialogBinding.apply {
                nameInput.setText(existingContact.name)
                phoneInput.setText(existingContact.phoneNumber)
                groupInput.setText(existingContact.group)
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (contact == null) "Add Contact" else "Edit Contact")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val newContact = Contact(
                    id = contact?.id?.toString() ?: "",
                    name = dialogBinding.nameInput.text.toString(),
                    phoneNumber = dialogBinding.phoneInput.text.toString(),
                    group = dialogBinding.groupInput.text.toString(),
                    variables = contact?.variables ?: emptyMap()
                )
                viewModel.saveContact(newContact)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_CODE_PICK_CSV = 1001
    }
}