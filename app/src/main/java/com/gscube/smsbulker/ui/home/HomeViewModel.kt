package com.gscube.smsbulker.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.repository.BulkSmsRepository
import com.gscube.smsbulker.repository.SmsRepository
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
    private val smsRepository: SmsRepository,
    private val bulkSmsRepository: BulkSmsRepository,
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

    fun sendBulkSms() {
        val currentState = _state.value
        val template = currentState.selectedTemplate ?: return
        val recipients = currentState.recipients
        val senderID = currentState.senderID

        if (recipients.isEmpty() || senderID.isNullOrBlank()) return

        viewModelScope.launch {
            try {
                // Convert recipients to personalized pairs
                val personalizedRecipients = recipients.map { recipient ->
                    recipient.phoneNumber to mapOf(
                        "name" to (recipient.name ?: ""),
                        "phone" to recipient.phoneNumber
                    )
                }

                // Send personalized messages
                smsRepository.sendPersonalizedSms(
                    recipients = personalizedRecipients,
                    messageTemplate = template.content,
                    sender = senderID
                ).collect { results ->
                    _state.update { it.copy(
                        results = results,
                        error = null
                    ) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun retryFailedMessages() {
        val currentState = _state.value
        val template = currentState.selectedTemplate ?: return
        val failedResults = currentState.results.filter { it.status == "FAILED" }

        if (failedResults.isEmpty()) return

        viewModelScope.launch {
            try {
                // Resend failed messages as personalized messages
                val personalizedRecipients = failedResults.map { result ->
                    result.recipient to mapOf(
                        "name" to "",  // Since BulkSmsResult doesn't have name, use empty string
                        "phone" to result.recipient
                    )
                }

                smsRepository.sendPersonalizedSms(
                    recipients = personalizedRecipients,
                    messageTemplate = template.content,
                    sender = currentState.senderID
                ).collect { results ->
                    _state.update { it.copy(
                        results = results,
                        error = null
                    ) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun setSelectedTemplate(template: MessageTemplate?) {
        _state.update { it.copy(selectedTemplate = template) }
    }

    fun setRecipients(recipients: List<Recipient>) {
        _state.update { it.copy(recipients = recipients) }
    }

    fun setSenderID(senderID: String) {
        _state.update { it.copy(senderID = senderID) }
    }

    private fun getMessagePreview(recipient: Recipient): String {
        val template = _state.value.selectedTemplate ?: return ""
        return template.content.replace("{name}", recipient.name ?: "")
            .replace("{phone}", recipient.phoneNumber)
    }

    fun loadRecipients(uri: Uri) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                val recipients = parseRecipientsFromUri(uri)
                _state.update { it.copy(
                    recipients = recipients,
                    isLoading = false,
                    error = null
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Failed to load recipients: ${e.message}"
                ) }
            }
        }
    }

    private suspend fun parseRecipientsFromUri(uri: Uri): List<Recipient> {
        // Implementation of CSV parsing logic
        return emptyList() // TODO: Implement actual CSV parsing
    }

    fun getPreview(recipient: Recipient): String {
        val template = _state.value.selectedTemplate ?: return ""
        return template.content.replace("{name}", recipient.name ?: "")
            .replace("{phone}", recipient.phoneNumber)
    }

    fun updateState(update: (HomeViewState) -> HomeViewState) {
        _state.update(update)
    }
}