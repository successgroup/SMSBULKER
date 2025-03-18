package com.gscube.smsbulker.ui.home

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.repository.*
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.ui.auth.LoginActivity
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
    val senderID: String? = null,
    val needsLogin: Boolean = false
)

@Singleton
@ContributesBinding(AppScope::class)
class HomeViewModel @Inject constructor(
    application: Application,
    private val templateRepository: TemplateRepository,
    private val bulkSmsRepository: BulkSmsRepository,
    private val contactsRepository: ContactsRepository,
    private val userRepository: UserRepository,
    private val firebaseRepository: FirebaseRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeViewState())
    val state: StateFlow<HomeViewState> = _state.asStateFlow()

    init {
        loadUserInfo()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                
                // First try to get from UserRepository (SecureStorage)
                val user = try {
                    userRepository.getCurrentUser()  // This returns non-null User
                } catch (e: Exception) {
                    _state.update { it.copy(
                        error = "Failed to load user info: ${e.message}",
                        isLoading = false
                    )}
                    return@launch
                }
                
                if (user.companyAlias.isBlank()) {
                    // If no data in SecureStorage, try to get from Firebase
                    firebaseRepository.getCurrentUser().onSuccess { profile ->
                        if (profile == null) {
                            // User is not logged in or no profile exists
                            _state.update { it.copy(
                                error = "Please log in to continue",
                                isLoading = false,
                                needsLogin = true
                            )}
                        } else {
                            _state.update { it.copy(
                                senderID = profile.company,
                                isLoading = false
                            )}
                        }
                    }.onFailure { e ->
                        if (e.message?.contains("Permission denied") == true) {
                            _state.update { it.copy(
                                error = "Please log in to continue",
                                isLoading = false,
                                needsLogin = true
                            )}
                        } else {
                            _state.update { it.copy(
                                error = "Failed to load user profile: ${e.message}",
                                isLoading = false
                            )}
                        }
                    }
                } else {
                    // Use data from SecureStorage
                    _state.update { it.copy(
                        senderID = user.companyAlias,
                        isLoading = false
                    )}
                }
            } catch (e: Exception) {
                _state.update { it.copy(
                    error = "Failed to load user info: ${e.message}",
                    isLoading = false
                )}
            }
        }
    }

    private fun navigateToLogin() {
        // Removed navigation to login activity
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

    fun setTemplate(template: MessageTemplate) {
        _state.update { it.copy(
            selectedTemplate = template,
            success = "Template selected: ${template.title}"
        )}
    }

    fun setSelectedTemplate(template: MessageTemplate) {
        viewModelScope.launch {
            _state.update { it.copy(
                selectedTemplate = template,
                error = null
            )}
        }
    }

    fun updateMessageContent(content: String) {
        _state.update { currentState ->
            currentState.copy(
                selectedTemplate = currentState.selectedTemplate?.copy(content = content)
            )
        }
    }

    fun addRecipients(contacts: List<Contact>) {
        val newRecipients = contacts.map { contact ->
            Recipient(
                name = contact.name,
                phoneNumber = contact.phoneNumber,
                variables = contact.variables
            )
        }
        _state.update { currentState ->
            val existingPhoneNumbers = currentState.recipients.map { it.phoneNumber }.toSet()
            val uniqueNewRecipients = newRecipients.filter { !existingPhoneNumbers.contains(it.phoneNumber) }
            
            if (uniqueNewRecipients.size < newRecipients.size) {
                // Some duplicates were found
                val duplicateCount = newRecipients.size - uniqueNewRecipients.size
                _state.value = currentState.copy(
                    error = "Skipped $duplicateCount duplicate contact(s)"
                )
            }
            
            currentState.copy(
                recipients = currentState.recipients + uniqueNewRecipients
            )
        }
    }

    fun removeRecipient(recipient: Recipient) {
        _state.update { currentState ->
            currentState.copy(
                recipients = currentState.recipients.filter { it != recipient }
            )
        }
    }

    fun sendBulkSms() {
        val currentState = state.value
        val message = currentState.selectedTemplate?.content ?: return
        val recipients = currentState.recipients
        
        if (recipients.isEmpty()) {
            _state.update { it.copy(error = "No recipients selected") }
            return
        }

        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }
                
                val senderID = currentState.senderID ?: try {
                    val user = userRepository.getCurrentUser()  // This returns non-null User
                    if (user.companyAlias.isBlank()) {
                        throw IllegalStateException("Company alias is not set")
                    }
                    user.companyAlias
                } catch (e: Exception) {
                    _state.update { it.copy(
                        error = "Failed to get sender ID: ${e.message}",
                        isLoading = false
                    )}
                    return@launch
                }

                val request = BulkSmsRequest(
                    message = message,
                    recipients = recipients,
                    senderId = senderID,
                    messageTemplate = currentState.selectedTemplate
                )
                
                val response = bulkSmsRepository.sendBulkSms(request)
                response.onSuccess { bulkSmsResponse ->
                    // Create BulkSmsResults from response
                    val results = bulkSmsResponse.messageStatuses.map { status ->
                        BulkSmsResult(
                            recipient = status.recipient,
                            status = status.status.toString(),
                            messageId = status.messageId,
                            timestamp = status.timestamp
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
        _state.update { it.copy(success = null) }
    }
}