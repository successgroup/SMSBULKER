package com.gscube.smsbulker.ui.sendMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.repository.SmsRepository
import com.gscube.smsbulker.repository.UserRepository
import com.gscube.smsbulker.di.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class SendMessageUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val selectedTemplate: MessageTemplate? = null,
    val message: String = "",
    val senderId: String = "",
    val selectedContacts: List<Contact> = emptyList(),
    val scheduleTime: Long? = null
)

data class InvalidRecipient(
    val recipient: Recipient,
    val missingPlaceholders: Set<String>
)

data class PlaceholderValidationResult(
    val validRecipients: List<Recipient>,
    val invalidRecipients: List<InvalidRecipient>
)

@Singleton
@ContributesMultibinding(AppScope::class)
class SendMessageViewModel @Inject constructor(
    private val smsRepository: SmsRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendMessageUiState())
    val uiState: StateFlow<SendMessageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                _uiState.update { it.copy(senderId = user.companyAlias) }
            } catch (e: Exception) {
                setError("Failed to load user info: ${e.message}")
            }
        }
    }

    fun setPreSelectedContacts(contacts: List<Contact>) {
        _uiState.update { it.copy(selectedContacts = contacts) }
    }

    fun setMessage(message: String) {
        _uiState.update { it.copy(message = message, selectedTemplate = null) }
    }

    fun setTemplate(template: MessageTemplate) {
        _uiState.update { it.copy(selectedTemplate = template, message = template.content) }
    }

    fun setSenderId(senderId: String) {
        _uiState.update { it.copy(senderId = senderId) }
    }

    fun setScheduleTime(timestamp: Long?) {
        _uiState.update { it.copy(scheduleTime = timestamp) }
    }

    private fun containsPlaceholders(message: String): Boolean {
        // Check for both {name} and <%name%> formats
        return Regex("\\{(\\w+)\\}").find(message) != null || 
               Regex("<%([\\w]+)%>").find(message) != null
    }

    fun sendMessage() {
        val currentState = _uiState.value
        if (currentState.selectedContacts.isEmpty()) {
            setError("No recipients selected")
            return
        }

        if (currentState.message.isBlank()) {
            setError("Please enter a message or select a template")
            return
        }

        if (currentState.senderId.isBlank()) {
            setError("Sender ID is required")
            return
        }

        viewModelScope.launch {
            try {
                setLoading(true)
                clearMessages()
    
                // Check if the message contains placeholders, regardless of template selection
                val hasPlaceholders = containsPlaceholders(currentState.message)
                
                if (hasPlaceholders) {
                    // Handle as a personalized message with placeholders
                    // Convert contacts to recipients
                    val recipients = currentState.selectedContacts.map { contact ->
                        Recipient(
                            phoneNumber = contact.phoneNumber, 
                            name = contact.name, 
                            variables = contact.variables
                        )
                    }
    
                    // For Arkesel format placeholders, we need to extract the variable names
                    val message = currentState.message
                    val placeholders = extractPlaceholders(message)
                    
                    val validationResult = validatePlaceholders(
                        message, 
                        recipients,
                        placeholders
                    )
                    
                    if (validationResult.invalidRecipients.isNotEmpty()) {
                        val errorMessages = validationResult.invalidRecipients.map { invalidRecipient ->
                            "Recipient ${invalidRecipient.recipient.name ?: invalidRecipient.recipient.phoneNumber} is missing placeholders: ${invalidRecipient.missingPlaceholders.joinToString(", ")}"
                        }
                        setError("Placeholder validation failed:\n${errorMessages.joinToString("\n")}")
                        return@launch
                    }
    
                    // Use only valid recipients for sending
                    val personalizedRecipients = validationResult.validRecipients.map { recipient ->
                        recipient.phoneNumber to (recipient.variables ?: emptyMap())
                    }
    
                    val messageFlow = smsRepository.sendPersonalizedSms(
                        recipients = personalizedRecipients,
                        messageTemplate = currentState.message,
                        sender = currentState.senderId
                    )

                    messageFlow.collect { results ->
                        val successful = results.count { it.status == "DELIVERED" }
                        val failed = results.count { it.status == "FAILED" }
                        val totalCost = results.sumOf { it.cost }
                        
                        if (successful > 0) {
                            setSuccess("Successfully sent $successful messages${if (failed > 0) ", $failed failed" else ""}. Total cost: $totalCost credits")
                        } else {
                            setError("Failed to send messages")
                        }
                    }
                } else {
                    // Send bulk message for plain text without placeholders
                    val messageFlow = smsRepository.sendBulkSms(
                        recipients = currentState.selectedContacts.map { it.phoneNumber },
                        message = currentState.message,
                        sender = currentState.senderId
                    )

                    messageFlow.collect { results ->
                        val successful = results.count { it.status == "DELIVERED" }
                        val failed = results.count { it.status == "FAILED" }
                        val totalCost = results.sumOf { it.cost }
                        
                        if (successful > 0) {
                            setSuccess("Successfully sent $successful messages${if (failed > 0) ", $failed failed" else ""}. Total cost: $totalCost credits")
                        } else {
                            setError("Failed to send messages")
                        }
                    }
                }
            } catch (e: Exception) {
                setError("Failed to send messages: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        _uiState.update { it.copy(isLoading = loading) }
    }

    private fun setError(error: String?) {
        _uiState.update { it.copy(error = error, success = null) }
    }

    private fun setSuccess(success: String?) {
        _uiState.update { it.copy(success = success, error = null) }
    }

    private fun clearMessages() {
        _uiState.update { it.copy(error = null, success = null) }
    }

    private fun extractPlaceholders(message: String): Set<String> {
        val curlyBracePlaceholders = Regex("\\{(\\w+)\\}").findAll(message)
            .map { it.groupValues[1] }
        
        val arkeselPlaceholders = Regex("<%([\\w]+)%>").findAll(message)
            .map { it.groupValues[1] }
        
        return (curlyBracePlaceholders + arkeselPlaceholders).toSet()
    }

    private fun validatePlaceholders(template: String, recipients: List<Recipient>, placeholders: Set<String>? = null): PlaceholderValidationResult {
        // Use provided placeholders or extract them from the template
        val allPlaceholders = placeholders ?: run {
            val curlyBracePlaceholders = Regex("\\{(\\w+)\\}").findAll(template)
                .map { it.groupValues[1] }
            
            val arkeselPlaceholders = Regex("<%([\\w]+)%>").findAll(template)
                .map { it.groupValues[1] }
            
            (curlyBracePlaceholders + arkeselPlaceholders).toSet()
        }

        val validRecipients = mutableListOf<Recipient>()
        val invalidRecipients = mutableListOf<InvalidRecipient>()

        recipients.forEach { recipient ->
            val recipientVariables = recipient.variables ?: emptyMap()
            
            val missingPlaceholders = allPlaceholders.filter { placeholder ->
                !recipientVariables.containsKey(placeholder)
            }.toSet()

            if (missingPlaceholders.isEmpty()) {
                validRecipients.add(recipient)
            } else {
                invalidRecipients.add(
                    InvalidRecipient(
                        recipient = recipient,
                        missingPlaceholders = missingPlaceholders
                    )
                )
            }
        }

        return PlaceholderValidationResult(
            validRecipients = validRecipients,
            invalidRecipients = invalidRecipients
        )
    }
}
