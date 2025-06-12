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
import com.gscube.smsbulker.utils.PhoneNumberValidator
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException
import android.util.Log
import com.gscube.smsbulker.utils.SecureStorage

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
    val availableCredits: Int = 0,
    val skippedContacts: List<SkippedContact> = emptyList()  // Add this line
)

// Add this import at the top of the file


// Modify the constructor to include SecureStorage
@Singleton
@ContributesBinding(AppScope::class)
class HomeViewModel @Inject constructor(
    application: Application,
    private val templateRepository: TemplateRepository,
    private val bulkSmsRepository: BulkSmsRepository,
    private val contactsRepository: ContactsRepository,
    private val userRepository: UserRepository,
    private val firebaseRepository: FirebaseRepository,
    private val accountRepository: AccountRepository,
    private val secureStorage: SecureStorage  // Add this parameter
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeViewState())
    val state: StateFlow<HomeViewState> = _state.asStateFlow()

    // Remove this duplicate declaration
    // @Inject
    // lateinit var accountRepository: AccountRepository

    private fun loadCreditBalance() {
        viewModelScope.launch {
            firebaseRepository.getCreditBalance().onSuccess { balance ->
                // Don't convert to Int
                _state.update { it.copy(availableCredits = balance.availableCredits) }
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
                _state.update { it.copy(isLoading = true, error = null) }

                // Prioritize getting user profile from Firebase
                firebaseRepository.getCurrentUser().onSuccess { profile ->
                    if (profile == null) {
                        // User is not logged in or no profile exists in Firebase
                        _state.update { it.copy(
                            error = "Please log in to continue",
                            isLoading = false,
                            needsLogin = true,
                            senderID = null // Clear sender ID if user is not logged in
                        )}
                    } else if (!profile.companyAlias.isBlank()) {
                        // Update the sender ID with the company alias from Firebase
                        android.util.Log.d("HomeViewModel", "Firebase companyAlias: '${profile.companyAlias}'")
                        _state.update { it.copy(
                            senderID = profile.companyAlias,
                            isLoading = false,
                            needsLogin = false
                        )}
                    } else {
                        // Profile exists but company alias is blank in Firebase
                        android.util.Log.d("HomeViewModel", "Firebase companyAlias is blank")
                        _state.update { it.copy(
                            error = "Please set your Company Alias in the Account section.",
                            isLoading = false,
                            needsLogin = false,
                            senderID = null // Clear sender ID if alias is blank
                        )}
                    }
                }.onFailure { e ->
                    // Handle failure to fetch from Firebase (e.g., network issues)
                    android.util.Log.e("HomeViewModel", "Failed to fetch user from Firebase", e)
                    _state.update { it.copy(
                        error = "Failed to load user info from server: ${e.message}",
                        isLoading = false,
                        needsLogin = false,
                        senderID = null // Clear sender ID on failure
                    )}
                }

            } catch (e: Exception) {
                // Catch any other unexpected exceptions
                android.util.Log.e("HomeViewModel", "Unexpected error loading user info", e)
                _state.update { it.copy(
                    error = "An unexpected error occurred: ${e.message}",
                    isLoading = false,
                    needsLogin = false,
                    senderID = null // Clear sender ID on error
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
        // Filter out invalid phone numbers
        val validContacts = contacts.filter { PhoneNumberValidator.isValidGhanaNumber(it.phoneNumber) }
        val invalidContacts = contacts.filter { !PhoneNumberValidator.isValidGhanaNumber(it.phoneNumber) }

        val newRecipients = validContacts.map { contact ->
            // Format the phone number
            val formattedNumber = PhoneNumberValidator.formatGhanaNumber(contact.phoneNumber) ?: contact.phoneNumber
            Recipient(
                name = contact.name,
                phoneNumber = formattedNumber,
                variables = contact.variables
            )
        }

        _state.update { currentState ->
            val existingPhoneNumbers = currentState.recipients.map { it.phoneNumber }.toSet()
            val uniqueNewRecipients = newRecipients.filter { !existingPhoneNumbers.contains(it.phoneNumber) }
            val duplicateRecipients = newRecipients.filter { existingPhoneNumbers.contains(it.phoneNumber) }

            val newSkippedContacts = mutableListOf<SkippedContact>()

            // Add invalid contacts to skipped list
            invalidContacts.forEach { contact ->
                newSkippedContacts.add(SkippedContact(
                    phoneNumber = contact.phoneNumber,
                    originalPhoneNumber = contact.phoneNumber,
                    name = contact.name ?: "Unknown", // Provide a default name if null
                    reason = SkipReason.INVALID_FORMAT
                ))
            }

            // Add duplicate contacts to skipped list
            duplicateRecipients.forEach { recipient ->
                newSkippedContacts.add(SkippedContact(
                    phoneNumber = recipient.phoneNumber,
                    originalPhoneNumber = recipient.phoneNumber,
                    name = recipient.name ?: "Unknown", // Provide a default name if null
                    reason = SkipReason.DUPLICATE_CONTACT
                ))
            }

            val message = buildString {
                if (duplicateRecipients.isNotEmpty()) {
                    append("Skipped ${duplicateRecipients.size} duplicate contact(s). ")
                }

                if (invalidContacts.isNotEmpty()) {
                    append("Skipped ${invalidContacts.size} invalid phone number(s).")
                }
            }

            if (message.isNotEmpty()) {
                _state.value = currentState.copy(error = message)
            }

            currentState.copy(
                recipients = currentState.recipients + uniqueNewRecipients,
                skippedContacts = currentState.skippedContacts + newSkippedContacts
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

    fun clearSelectedTemplate() {
        _state.update { it.copy(
            selectedTemplate = null
        )}
    }

    // Add this function to help with debugging
    // Fix the logDebugInfo function
    private fun logDebugInfo() {
        viewModelScope.launch {
            try {
                val apiKey = secureStorage.getApiKey()
                Log.d("HomeViewModel", "API Key available: ${!apiKey.isNullOrEmpty()}")
                
                val user = userRepository.getCurrentUser()
                Log.d("HomeViewModel", "User info - Name: ${user.name}, Company: ${user.company}, Alias: ${user.companyAlias}")
                
                val credits = firebaseRepository.getCurrentUserCredits()
                Log.d("HomeViewModel", "Credits: ${credits.getOrNull() ?: "Unknown"}")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error logging debug info: ${e.message}")
            }
        }
    }

    // Call this at the beginning of sendBulkSms
    fun sendBulkSms() {
        val currentState = state.value
        // Fix: Check both template content and manual message
        val message = currentState.selectedTemplate?.content ?: currentState.message

        if (message.isNullOrBlank()) {
            _state.update { it.copy(error = "Please enter a message or select a template") }
            return
        }

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

                // Remove the duplicate senderID declaration below
                // val senderID = user.companyAlias

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
                            else -> "Failed to send messages. Please try again 1."
                        }
                        else -> when {
                            error.message?.contains("ECONNREFUSED") == true -> "Could not connect to the server. Please try again later."
                            error.message?.contains("timeout") == true -> "Request timed out. Please check your internet connection."
                            else -> "Failed to send messages. Please try again 2."
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
                    else -> "Failed to send messages. Please try again 3."
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

    fun getSkippedContacts(): List<SkippedContact> {
        return state.value.skippedContacts
    }

    fun exportSkippedContactsToCSV() {
        viewModelScope.launch {
            try {
                val skippedContacts = state.value.skippedContacts
                if (skippedContacts.isEmpty()) {
                    _state.update { it.copy(error = "No skipped contacts to export") }
                    return@launch
                }

                contactsRepository.exportSkippedContactsToCSV(skippedContacts)
                _state.update { it.copy(success = "Skipped contacts exported successfully") }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to export skipped contacts: ${e.message}") }
            }
        }
    }

    fun clearSkippedContacts() {
        _state.update { it.copy(skippedContacts = emptyList()) }
    }

    fun removeSkippedContact(contact: SkippedContact) {
        _state.update { currentState ->
            currentState.copy(
                skippedContacts = currentState.skippedContacts.filter { it != contact }
            )
        }
    }
}

