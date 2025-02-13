package com.gscube.smsbulker.ui.sendMessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.repository.SmsRepository
import com.gscube.smsbulker.repository.UserRepository
import com.gscube.smsbulker.di.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
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

@Singleton
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
        _uiState.update { it.copy(selectedTemplate = template, message = "") }
    }

    fun setSenderId(senderId: String) {
        _uiState.update { it.copy(senderId = senderId) }
    }

    fun setScheduleTime(timestamp: Long?) {
        _uiState.update { it.copy(scheduleTime = timestamp) }
    }

    fun sendMessage() {
        val currentState = _uiState.value
        if (currentState.selectedContacts.isEmpty()) {
            setError("No recipients selected")
            return
        }

        if (currentState.selectedTemplate == null && currentState.message.isBlank()) {
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

                val messageFlow = if (currentState.selectedTemplate != null) {
                    // Send personalized messages using template
                    val personalizedRecipients = currentState.selectedContacts.map { contact ->
                        contact.phoneNumber to (contact.variables ?: emptyMap())
                    }
                    smsRepository.sendPersonalizedSms(
                        recipients = personalizedRecipients,
                        messageTemplate = currentState.selectedTemplate.content,
                        sender = currentState.senderId
                    )
                } else {
                    // Send bulk message
                    smsRepository.sendBulkSms(
                        recipients = currentState.selectedContacts.map { it.phoneNumber },
                        message = currentState.message,
                        sender = currentState.senderId
                    )
                }

                messageFlow.collect { results ->
                    val successful = results.count { SmsStatus.fromString(it.status) == SmsStatus.DELIVERED }
                    val failed = results.count { SmsStatus.fromString(it.status) == SmsStatus.FAILED }
                    
                    if (successful > 0) {
                        setSuccess("Successfully sent $successful messages${if (failed > 0) ", $failed failed" else ""}")
                    } else {
                        setError("Failed to send messages")
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
}
