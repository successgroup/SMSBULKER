package com.gscube.smsbulker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SmsUser(
    val id: String,
    val name: String,
    val email: String,
    val companyName: String? = null,
    val companyAlias: String,  // Used as sender ID
    val phone: String? = null,
    val credits: Int = 0,
    val isActive: Boolean = true
) : Parcelable

@Parcelize
data class BulkSmsRequest(
    val message: String? = null,
    val messageTemplate: MessageTemplate? = null,
    val recipients: List<Recipient>,
    val senderId: String,
    val scheduleTime: Long? = null
) : Parcelable

@Parcelize
data class BulkSmsResponse(
    val batchId: String,
    val status: BatchStatus,
    val totalMessages: Int,
    val messageStatuses: List<MessageStatus> = emptyList(),
    val cost: Double = 0.0,
    val units: Int = 0,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

@Parcelize
data class BulkSmsResult(
    val recipient: String,
    val status: String,
    val messageId: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val cost: Double = 0.0
) : Parcelable

@Parcelize
data class Recipient(
    val phoneNumber: String,
    val name: String? = null,
    val variables: Map<String, String> = emptyMap()
) : Parcelable

@Parcelize
data class MessageStatus(
    val messageId: String,
    val recipient: String,
    val status: BatchStatus,
    val timestamp: Long,
    val errorMessage: String? = null
) : Parcelable

@Parcelize
data class SmsApiResponse(
    val success: Boolean,
    val message: String,
    val messageId: String? = null,
    val status: String? = null,
    val errorCode: String? = null,
    val cost: Double? = null,
    val credits: Int? = null
) : Parcelable

enum class BatchStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED
}

enum class SmsStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED;

    companion object {
        fun fromString(status: String): SmsStatus = when (status.lowercase()) {
            "sent", "success" -> SENT
            "delivered" -> DELIVERED
            "pending" -> PENDING
            else -> FAILED
        }
    }
}

@Parcelize
data class SmsMessage(
    val id: String = "",
    val sender: String = "",
    val recipient: String = "",
    val message: String = "",
    val status: SmsStatus = SmsStatus.PENDING,
    val timestamp: Long = 0,
    val creditsUsed: Int = 0,
    val errorMessage: String? = null
) : Parcelable

@Parcelize
data class Template(
    val id: String,
    val title: String,
    val content: String,
    val variables: List<String>,
    val createdAt: Long,
    val updatedAt: Long
) : Parcelable

@Parcelize
data class PersonalizedSmsRequest(
    val sender: String,
    val message: String,
    val recipients: Map<String, Map<String, String>>,
    val callback_url: String? = null
) : Parcelable