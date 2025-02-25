package com.gscube.smsbulker.ui.contacts

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.Contact
import com.gscube.smsbulker.repository.ContactsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import javax.inject.Inject
import android.os.Environment
import android.app.Application

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
)

sealed interface ContactsEvent {
    data class ShowError(val message: String) : ContactsEvent
    data class ShowSuccess(val message: String) : ContactsEvent
    object DismissMessage : ContactsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ContactsEvent>()
    val events: SharedFlow<ContactsEvent> = _events.asSharedFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        observeContacts()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeContacts() {
        viewModelScope.launch {
            searchQuery
                .flatMapLatest { query ->
                    contactsRepository.getContactsFlow()
                        .map { contacts ->
                            if (query.isBlank()) {
                                contacts
                            } else {
                                contacts.filter { contact ->
                                    contact.name.contains(query, ignoreCase = true) ||
                                    contact.phoneNumber.contains(query) ||
                                    contact.group.contains(query, ignoreCase = true) ||
                                    contact.variables.any { (key, value) ->
                                        key.contains(query, ignoreCase = true) ||
                                        value.contains(query, ignoreCase = true)
                                    }
                                }
                            }
                        }
                }
                .onStart { 
                    _uiState.update { it.copy(isLoading = true) }
                }
                .catch { error -> 
                    _uiState.update { it.copy(isLoading = false) }
                    handleError(error)
                }
                .collect { contacts ->
                    _uiState.update { it.copy(
                        contacts = contacts,
                        isLoading = false
                    )}
                }
        }
    }

    fun updateSearchQuery(query: String) {
        viewModelScope.launch {
            searchQuery.emit(query)
            _uiState.update { it.copy(searchQuery = query) }
        }
    }

    fun saveContact(contact: Contact) {
        viewModelScope.launch {
            try {
                setLoading(true)
                contactsRepository.saveContact(contact)
                emitEvent(ContactsEvent.ShowSuccess("Contact saved successfully"))
            } catch (e: Exception) {
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            try {
                setLoading(true)
                contactsRepository.deleteContact(contact)
                emitEvent(ContactsEvent.ShowSuccess("Contact deleted successfully"))
            } catch (e: Exception) {
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }

    fun deleteContacts(contacts: List<Contact>) {
        viewModelScope.launch {
            try {
                contacts.forEach { contact ->
                    contactsRepository.deleteContact(contact)
                }
                _events.emit(ContactsEvent.ShowSuccess("${contacts.size} contacts deleted"))
            } catch (e: Exception) {
                _events.emit(ContactsEvent.ShowError("Failed to delete contacts: ${e.message}"))
            }
        }
    }

    fun importContacts(uri: Uri) {
        viewModelScope.launch {
            try {
                setLoading(true)
                contactsRepository.importContactsFromCsv(uri)
                emitEvent(ContactsEvent.ShowSuccess("Contacts imported successfully"))
            } catch (e: Exception) {
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }

    fun importContactsFromCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                setLoading(true)
                val contacts = contactsRepository.importContactsFromCsv(uri)
                emitEvent(ContactsEvent.ShowSuccess("Imported ${contacts.size} contacts from CSV"))
            } catch (e: Exception) {
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }

    fun exportContacts() {
        viewModelScope.launch {
            try {
                setLoading(true)
                contactsRepository.exportContactsToCsv()
                emitEvent(ContactsEvent.ShowSuccess("Contacts exported successfully"))
            } catch (e: Exception) {
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }

    fun exportContactsToCSV(contacts: List<Contact>) {
        viewModelScope.launch {
            try {
                val fileName = "contacts_export_${System.currentTimeMillis()}.csv"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                
                file.bufferedWriter().use { writer ->
                    // Write header
                    writer.write("Name,Phone Number,Group,Variables\n")
                    
                    // Write contacts
                    contacts.forEach { contact ->
                        val variables = contact.variables.entries.joinToString(";") { "${it.key}=${it.value}" }
                        writer.write("${contact.name},${contact.phoneNumber},${contact.group},${variables}\n")
                    }
                }
                
                // Notify media scanner
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("text/csv")
                ) { path, uri -> 
                    viewModelScope.launch {
                        _events.emit(ContactsEvent.ShowSuccess("Contacts exported to $path"))
                    }
                }
            } catch (e: Exception) {
                _events.emit(ContactsEvent.ShowError("Failed to export contacts: ${e.message}"))
            }
        }
    }

    fun importFromPhoneContacts() {
        viewModelScope.launch {
            try {
                setLoading(true)
                contactsRepository.importFromPhoneContacts()
                emitEvent(ContactsEvent.ShowSuccess("Phone contacts imported successfully"))
            } catch (e: Exception) {
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }

    fun exportToPhoneContacts(contacts: List<Contact>) {
        viewModelScope.launch {
            try {
                setLoading(true)
                contactsRepository.exportToPhoneContacts(contacts)
                emitEvent(ContactsEvent.ShowSuccess("Contacts exported to phone successfully"))
            } catch (e: Exception) {
                handleError(e)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        _uiState.update { it.copy(isLoading = loading) }
    }

    private suspend fun handleError(error: Throwable) {
        _uiState.update { it.copy(error = error.message) }
        emitEvent(ContactsEvent.ShowError(error.message ?: "An unknown error occurred"))
    }

    private suspend fun emitEvent(event: ContactsEvent) {
        _events.emit(event)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissMessage() {
        viewModelScope.launch {
            emitEvent(ContactsEvent.DismissMessage)
        }
    }
}