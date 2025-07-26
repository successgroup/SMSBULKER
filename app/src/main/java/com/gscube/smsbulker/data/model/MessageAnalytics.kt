package com.gscube.smsbulker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Data model for message analytics
 * This model is used to track and analyze message delivery status and performance
 */
@Parcelize
data class MessageAnalytics(
    val id: String = "", // Unique identifier for this analytics record
    val batchId: String, // Associated batch ID
    val messageId: String, // Associated message ID
    val recipient: String, // Recipient phone number
    val sender: String, // Sender ID used
    val status: SmsStatus, // Current status of the message
    val sentTimestamp: Long, // When the message was sent
    val deliveredTimestamp: Long? = null, // When the message was delivered (if delivered)
    val failedTimestamp: Long? = null, // When the message failed (if failed)
    val deliveryLatency: Long? = null, // Time between sent and delivered in milliseconds
    val messageLength: Int, // Length of the message in characters
    val messageSegments: Int, // Number of SMS segments used
    val creditsUsed: Int, // Number of credits used for this message
    val errorCode: String? = null, // Error code if failed
    val errorMessage: String? = null, // Error message if failed
    val networkOperator: String? = null, // Network operator of the recipient if available
    val countryCode: String? = null, // Country code of the recipient
    val lastUpdated: Long = System.currentTimeMillis() // Last time this record was updated
) : Parcelable

/**
 * Data model for batch analytics
 * This model is used to track and analyze batch-level metrics
 */
@Parcelize
data class BatchAnalytics(
    val id: String = "", // Unique identifier for this analytics record (same as batchId)
    val batchId: String, // Associated batch ID
    val userId: String, // User who sent the batch
    val totalMessages: Int, // Total number of messages in the batch
    val deliveredMessages: Int = 0, // Number of delivered messages
    val failedMessages: Int = 0, // Number of failed messages
    val pendingMessages: Int = 0, // Number of pending messages
    val sentTimestamp: Long, // When the batch was sent
    val completedTimestamp: Long? = null, // When the batch was completed (all messages delivered or failed)
    val averageDeliveryLatency: Long? = null, // Average delivery latency in milliseconds
    val totalCreditsUsed: Int, // Total credits used for this batch
    val messageTemplate: String? = null, // Message template used (if any)
    val isPersonalized: Boolean = false, // Whether the batch used personalized messages
    val lastUpdated: Long = System.currentTimeMillis() // Last time this record was updated
) : Parcelable {
    val deliveryRate: Double
        get() = if (totalMessages > 0) deliveredMessages.toDouble() / totalMessages else 0.0

    val failureRate: Double
        get() = if (totalMessages > 0) failedMessages.toDouble() / totalMessages else 0.0
        
    val completionRate: Double
        get() = if (totalMessages > 0) (deliveredMessages + failedMessages).toDouble() / totalMessages else 0.0
        
    val status: BatchStatus
        get() = when {
            failedMessages == totalMessages -> BatchStatus.FAILED
            deliveredMessages == totalMessages -> BatchStatus.COMPLETED
            deliveredMessages + failedMessages == totalMessages -> BatchStatus.PARTIAL_SUCCESS
            deliveredMessages + failedMessages > 0 -> BatchStatus.PROCESSING
            else -> BatchStatus.QUEUED
        }
}

/**
 * Data model for message delivery report
 * This model is used to track message delivery status updates
 */
@Parcelize
data class MessageDeliveryReport(
    val id: String = "", // Unique identifier for this report
    val messageId: String, // Associated message ID
    val batchId: String? = null, // Associated batch ID (if available)
    val recipient: String, // Recipient phone number
    val status: SmsStatus, // Status reported
    val timestamp: Long, // When this status was reported
    val errorCode: String? = null, // Error code if failed
    val errorMessage: String? = null, // Error message if failed
    val rawPayload: String? = null // Raw payload from the delivery report webhook
) : Parcelable

/**
 * Data model for aggregated analytics by time period
 * This model is used for reporting and dashboards
 */
@Parcelize
data class TimeBasedAnalytics(
    val id: String = "", // Unique identifier for this analytics record
    val userId: String, // User associated with these analytics
    val period: AnalyticsPeriod, // Time period (DAILY, WEEKLY, MONTHLY)
    val startTime: Long, // Start of the time period
    val endTime: Long, // End of the time period
    val totalMessages: Int = 0, // Total messages sent in this period
    val deliveredMessages: Int = 0, // Number of delivered messages
    val failedMessages: Int = 0, // Number of failed messages
    val pendingMessages: Int = 0, // Number of pending messages
    val totalBatches: Int = 0, // Total batches sent in this period
    val completedBatches: Int = 0, // Number of completed batches
    val failedBatches: Int = 0, // Number of failed batches
    val partialBatches: Int = 0, // Number of partially successful batches
    val pendingBatches: Int = 0, // Number of pending batches
    val totalCreditsUsed: Int = 0, // Total credits used in this period
    val averageDeliveryLatency: Long? = null, // Average delivery latency in milliseconds
    val lastUpdated: Long = System.currentTimeMillis() // Last time this record was updated
) : Parcelable {
    val deliveryRate: Double
        get() = if (totalMessages > 0) deliveredMessages.toDouble() / totalMessages else 0.0

    val failureRate: Double
        get() = if (totalMessages > 0) failedMessages.toDouble() / totalMessages else 0.0
        
    val batchSuccessRate: Double
        get() = if (totalBatches > 0) completedBatches.toDouble() / totalBatches else 0.0
}

/**
 * Enum for analytics time periods
 */
enum class AnalyticsPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    CUSTOM
}

/**
 * Data model for error analytics
 * This model is used to track and categorize errors
 */
@Parcelize
data class ErrorAnalytics(
    val id: String = "", // Unique identifier for this analytics record
    val userId: String, // User associated with these analytics
    val errorCode: String, // Error code
    val errorMessage: String, // Error message
    val count: Int = 0, // Number of occurrences
    val firstOccurrence: Long, // First time this error occurred
    val lastOccurrence: Long, // Last time this error occurred
    val affectedRecipients: List<String> = emptyList(), // List of affected recipients (limited to 100)
    val lastUpdated: Long = System.currentTimeMillis() // Last time this record was updated
) : Parcelable