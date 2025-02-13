package com.gscube.smsbulker.ui.home

import com.gscube.smsbulker.data.model.BulkSmsResult
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.Recipient

data class HomeState(
    val recipients: List<Recipient> = emptyList(),
    val selectedTemplate: MessageTemplate? = null,
    val isSending: Boolean = false,
    val isLoading: Boolean = false,
    val sendingProgress: Int = 0,
    val totalRecipients: Int = 0,
    val results: List<BulkSmsResult> = emptyList(),
    val error: String? = null,
    val senderID: String? = null
)

sealed class HomeEvent {
    data class SendingStarted(val totalRecipients: Int) : HomeEvent()
    data class SendingProgress(val sent: Int, val total: Int) : HomeEvent()
    data class SendingCompleted(val results: List<BulkSmsResult>) : HomeEvent()
    data class SendingFailed(val error: String) : HomeEvent()
    data class TemplateSelected(val template: MessageTemplate) : HomeEvent()
    data class RecipientsLoaded(val recipients: List<Recipient>) : HomeEvent()
} 