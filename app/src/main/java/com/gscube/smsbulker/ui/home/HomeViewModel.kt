package com.gscube.smsbulker.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.repository.BulkSmsRepository
import com.gscube.smsbulker.repository.ContactsRepository
import com.gscube.smsbulker.repository.TemplateRepository
import com.gscube.smsbulker.repository.UserRepository
import com.gscube.smsbulker.di.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class HomeViewState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val selectedTemplate: MessageTemplate? = null,
    val recipients: List<Recipient> = emptyList(),
    val results: List<BulkSmsResult> = emptyList(),
    val senderID: String? = null
)

@Singleton
@ContributesBinding(AppScope::class)
class HomeViewModel @Inject constructor(
    application: Application,
    private val templateRepository: TemplateRepository,
    private val bulkSmsRepository: BulkSmsRepository,
    private val contactsRepository: ContactsRepository,
    private val userRepository: UserRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeViewState())
    val state: StateFlow<HomeViewState> = _state.asStateFlow()

    init {
        loadUserInfo()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                _state.update { it.copy(senderID = user.companyAlias) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to load user info: ${e.message}") }
            }
        }
    }

    fun loadRecipients(uri: Uri) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }
                bulkSmsRepository.parseRecipientsFromCsv(uri).collect { recipients ->
                    _state.update { it.copy(
                        recipients = recipients,
                        isLoading = false
                    )}
                }
            } catch (e: Exception) {
                _state.update { it.copy(
                    error = "Failed to load recipients: ${e.message}",
                    isLoading = false
                )}
            }
        }
    }

    fun importContacts() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }
                val contacts = contactsRepository.getContacts()
                val recipients = contacts.map { contact ->
                    Recipient(
                        name = contact.name,
                        phoneNumber = contact.phoneNumber,
                        variables = contact.variables
                    )
                }
                _state.update { it.copy(
                    recipients = recipients,
                    isLoading = false
                )}
            } catch (e: Exception) {
                _state.update { it.copy(
                    error = "Failed to import contacts: ${e.message}",
                    isLoading = false
                )}
            }
        }
    }

    fun setSelectedTemplate(template: MessageTemplate) {
        _state.update { it.copy(selectedTemplate = template) }
    }

    fun sendBulkSms() {
        val currentState = state.value
        val message = currentState.selectedTemplate?.content ?: return
        val recipients = currentState.recipients
        val senderID = currentState.senderID ?: return

        if (recipients.isEmpty()) {
            _state.update { it.copy(error = "No recipients selected") }
            return
        }

        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }
                
                val request = BulkSmsRequest(
                    message = message,
                    recipients = recipients,
                    senderId = senderID,
                    messageTemplate = currentState.selectedTemplate
                )
                
                val response = bulkSmsRepository.sendBulkSms(request)
                response.onSuccess { bulkSmsResponse ->
                    // Create BulkSmsResults from response
                    val results = recipients.map { recipient ->
                        BulkSmsResult(
                            recipient = recipient.phoneNumber,
                            status = bulkSmsResponse.status.toString(),
                            messageId = bulkSmsResponse.batchId,
                            timestamp = bulkSmsResponse.timestamp
                        )
                    }
                    
                    _state.update { it.copy(
                        results = results,
                        isLoading = false,
                        success = "Messages sent successfully"
                    )}
                }.onFailure { error ->
                    _state.update { it.copy(
                        error = "Failed to send messages: ${error.message}",
                        isLoading = false
                    )}
                }
            } catch (e: Exception) {
                _state.update { it.copy(
                    error = "Failed to send messages: ${e.message}",
                    isLoading = false
                )}
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _state.update { it.copy(success = null, results = emptyList()) }
    }
}