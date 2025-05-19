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
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

data class HomeViewState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val selectedTemplate: MessageTemplate? = null,
    val recipients: List<Recipient> = emptyList(),
    val senderID: String? = null,
    val needsLogin: Boolean = false,
    val sendingStage: SendingProgressDialog.SendStage? = null,
    val message: String = "",
    val availableCredits: Int = 0  // Add this line
)

@Singleton
@ContributesBinding(AppScope::class)
class HomeViewModel @Inject constructor(
    application: Application,
    private val templateRepository: TemplateRepository,
    private val bulkSmsRepository: BulkSmsRepository,
    private val contactsRepository: ContactsRepository,
    private val userRepository: UserRepository,
    private val firebaseRepository: FirebaseRepository,
    private val accountRepository: AccountRepository  // Keep this one
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeViewState())
    val state: StateFlow<HomeViewState> = _state.asStateFlow()

    // Remove this duplicate declaration
    // @Inject
    // lateinit var accountRepository: AccountRepository

    private fun loadCreditBalance() {
        viewModelScope.launch {
            accountRepository.getCreditBalance().onSuccess { balance ->
                _state.update { it.copy(availableCredits = balance.availableCredits.toInt()) }
            }.onFailure { error ->
                _state.update { it.copy(error = "Failed to load credit balance: ${error.message}") }
            }
        }
    }

    init {
        loadUserInfo()
        loadCreditBalance()
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
                val errorMessage = when (e) {
                    is UnknownHostException -> "No internet connection. Please check your network and try again."
                    is SocketTimeoutException -> "Connection timed out. Please check your internet connection and try again."
                    else -> "Failed to load recipients. Please try again."
                }
                
                _state.update { it.copy(
                    error = errorMessage,
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
                val errorMessage = when (e) {
                    is UnknownHostException -> "No internet connection. Please check your network and try again."
                    is SocketTimeoutException -> "Connection timed out. Please check your internet connection and try again."
                    else -> "Failed to import contacts. Please try again."
                }
                
                _state.update { it.copy(
                    error = errorMessage,
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

    fun updateMessageContent(message: String) {
        _state.update { it.copy(message = message) }
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

    fun clearRecipients() {
        _state.update { it.copy(recipients = emptyList()) }
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
                // Update to PREPARING stage
                _state.update { it.copy(
                    isLoading = true,
                    error = null,
                    sendingStage = SendingProgressDialog.SendStage.PREPARING
                )}
                
                val senderID = currentState.senderID ?: try {
                    val user = userRepository.getCurrentUser()
                    if (user.companyAlias.isBlank()) {
                        throw IllegalStateException("Company alias is not set")
                    }
                    user.companyAlias
                } catch (e: Exception) {
                    _state.update { it.copy(
                        error = "Failed to get sender ID: ${e.message}",
                        isLoading = false,
                        sendingStage = SendingProgressDialog.SendStage.ERROR
                    )}
                    return@launch
                }

                // Update to SENDING stage
                _state.update { it.copy(sendingStage = SendingProgressDialog.SendStage.SENDING) }

                val request = BulkSmsRequest(
                    message = message,
                    recipients = recipients,
                    senderId = senderID,
                    messageTemplate = currentState.selectedTemplate
                )
                
                // Make the API call
                val response = bulkSmsRepository.sendBulkSms(request)
                
                // Update to PROCESSING stage
                _state.update { it.copy(sendingStage = SendingProgressDialog.SendStage.PROCESSING) }
                
                response.onSuccess { bulkSmsResponse ->
                    // Update to COMPLETED stage with success message
                    _state.update { it.copy(
                        isLoading = false,
                        success = "Messages sent successfully to ${recipients.size} recipients",
                        sendingStage = SendingProgressDialog.SendStage.COMPLETED
                    )}
                }.onFailure { error ->
                    val errorMessage = when (error) {
                        is UnknownHostException -> "No internet connection. Please check your network and try again."
                        is SocketTimeoutException -> "Connection timed out. Please check your internet connection and try again."
                        is HttpException -> when (error.code()) {
                            401 -> "Authentication failed. Please check your API key."
                            403 -> "Access denied. Please verify your account status."
                            429 -> "Too many requests. Please try again later."
                            in 500..599 -> "Server error. Please try again later."
                            else -> "Failed to send messages. Please try again."
                        }
                        else -> when {
                            error.message?.contains("ECONNREFUSED") == true -> "Could not connect to the server. Please try again later."
                            error.message?.contains("timeout") == true -> "Request timed out. Please check your internet connection."
                            else -> "Failed to send messages. Please try again."
                        }
                    }
                    
                    _state.update { it.copy(
                        error = errorMessage,
                        isLoading = false,
                        sendingStage = SendingProgressDialog.SendStage.ERROR
                    )}
                }
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is UnknownHostException -> "No internet connection. Please check your network and try again."
                    is SocketTimeoutException -> "Connection timed out. Please check your internet connection and try again."
                    else -> "Failed to send messages. Please try again."
                }
                
                _state.update { it.copy(
                    error = errorMessage,
                    isLoading = false,
                    sendingStage = SendingProgressDialog.SendStage.ERROR
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