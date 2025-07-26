package com.gscube.smsbulker.repository

import com.gscube.smsbulker.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Repository interface for message analytics operations
 * This interface defines methods for tracking and analyzing message delivery status and performance
 */
interface MessageAnalyticsRepository {
    /**
     * Track a new message and create initial analytics record
     * @param messageId The message ID
     * @param batchId The batch ID
     * @param recipient The recipient phone number
     * @param sender The sender ID used
     * @param messageLength The length of the message in characters
     * @param messageSegments The number of SMS segments used
     * @param creditsUsed The number of credits used for this message
     * @return Result containing the created MessageAnalytics object or an error
     */
    suspend fun trackMessage(
        messageId: String,
        batchId: String,
        recipient: String,
        sender: String,
        messageLength: Int,
        messageSegments: Int,
        creditsUsed: Int
    ): Result<MessageAnalytics>
    
    /**
     * Track a new batch and create initial analytics record
     * @param batchId The batch ID
     * @param totalMessages The total number of messages in the batch
     * @param messageTemplate The message template used (if any)
     * @param isPersonalized Whether the batch used personalized messages
     * @param totalCreditsUsed The total credits used for this batch
     * @return Result containing the created BatchAnalytics object or an error
     */
    suspend fun trackBatch(
        batchId: String,
        totalMessages: Int,
        messageTemplate: String? = null,
        isPersonalized: Boolean = false,
        totalCreditsUsed: Int
    ): Result<BatchAnalytics>
    
    /**
     * Update message status based on a delivery report
     * @param report The delivery report
     * @return Result containing the updated MessageAnalytics object or an error
     */
    suspend fun updateMessageStatus(report: MessageDeliveryReport): Result<MessageAnalytics>
    
    /**
     * Update message status manually
     * @param messageId The message ID
     * @param status The new status
     * @param errorCode The error code (if failed)
     * @param errorMessage The error message (if failed)
     * @return Result containing the updated MessageAnalytics object or an error
     */
    suspend fun updateMessageStatus(
        messageId: String,
        status: SmsStatus,
        errorCode: String? = null,
        errorMessage: String? = null
    ): Result<MessageAnalytics>
    
    /**
     * Update batch status based on message statuses
     * @param batchId The batch ID
     * @return Result containing the updated BatchAnalytics object or an error
     */
    suspend fun updateBatchStatus(batchId: String): Result<BatchAnalytics>
    
    /**
     * Get message analytics by message ID
     * @param messageId The message ID
     * @return Result containing the MessageAnalytics object or an error
     */
    suspend fun getMessageAnalytics(messageId: String): Result<MessageAnalytics>
    
    /**
     * Get batch analytics by batch ID
     * @param batchId The batch ID
     * @return Result containing the BatchAnalytics object or an error
     */
    suspend fun getBatchAnalytics(batchId: String): Result<BatchAnalytics>
    
    /**
     * Get all message analytics for a batch
     * @param batchId The batch ID
     * @return Result containing a list of MessageAnalytics objects or an error
     */
    suspend fun getMessagesForBatch(batchId: String): Result<List<MessageAnalytics>>
    
    /**
     * Get all batch analytics for a user within a time range
     * @param startTime The start time (inclusive)
     * @param endTime The end time (inclusive)
     * @return Result containing a list of BatchAnalytics objects or an error
     */
    suspend fun getBatchesByTimeRange(startTime: Long, endTime: Long): Result<List<BatchAnalytics>>
    
    /**
     * Get time-based analytics for a specific period
     * @param period The analytics period (DAILY, WEEKLY, MONTHLY, etc.)
     * @param startTime The start time (inclusive)
     * @param endTime The end time (inclusive)
     * @return Result containing a list of TimeBasedAnalytics objects or an error
     */
    suspend fun getTimeBasedAnalytics(
        period: AnalyticsPeriod,
        startTime: Long,
        endTime: Long
    ): Result<List<TimeBasedAnalytics>>
    
    /**
     * Get error analytics for a user within a time range
     * @param startTime The start time (inclusive)
     * @param endTime The end time (inclusive)
     * @return Result containing a list of ErrorAnalytics objects or an error
     */
    suspend fun getErrorAnalytics(startTime: Long, endTime: Long): Result<List<ErrorAnalytics>>
    
    /**
     * Export message analytics for a batch as CSV
     * @param batchId The batch ID
     * @return Result containing the CSV string or an error
     */
    suspend fun exportBatchAnalyticsAsCsv(batchId: String): Result<String>
    
    /**
     * Export time-based analytics as CSV
     * @param period The analytics period (DAILY, WEEKLY, MONTHLY, etc.)
     * @param startTime The start time (inclusive)
     * @param endTime The end time (inclusive)
     * @return Result containing the CSV string or an error
     */
    suspend fun exportTimeBasedAnalyticsAsCsv(
        period: AnalyticsPeriod,
        startTime: Long,
        endTime: Long
    ): Result<String>
    
    /**
     * Get delivery rate for a specific time range
     * @param startTime The start time (inclusive)
     * @param endTime The end time (inclusive)
     * @return Result containing the delivery rate (0.0 to 1.0) or an error
     */
    suspend fun getDeliveryRate(startTime: Long, endTime: Long): Result<Double>
    
    /**
     * Get average delivery latency for a specific time range
     * @param startTime The start time (inclusive)
     * @param endTime The end time (inclusive)
     * @return Result containing the average delivery latency in milliseconds or an error
     */
    suspend fun getAverageDeliveryLatency(startTime: Long, endTime: Long): Result<Long>
    
    /**
     * Get total credits used for a specific time range
     * @param startTime The start time (inclusive)
     * @param endTime The end time (inclusive)
     * @return Result containing the total credits used or an error
     */
    suspend fun getTotalCreditsUsed(startTime: Long, endTime: Long): Result<Int>
    
    /**
     * Get message volume (total messages sent) for a specific time range
     * @param startTime The start time (inclusive)
     * @param endTime The end time (inclusive)
     * @return Result containing the message volume or an error
     */
    suspend fun getMessageVolume(startTime: Long, endTime: Long): Result<Int>
    
    /**
     * Get message analytics as a Flow for real-time updates
     * @param messageId The message ID
     * @return Flow of MessageAnalytics objects
     */
    fun observeMessageAnalytics(messageId: String): Flow<MessageAnalytics>
    
    /**
     * Get batch analytics as a Flow for real-time updates
     * @param batchId The batch ID
     * @return Flow of BatchAnalytics objects
     */
    fun observeBatchAnalytics(batchId: String): Flow<BatchAnalytics>
    
    /**
     * Process a delivery report from a webhook
     * @param rawPayload The raw payload from the webhook
     * @return Result containing the processed MessageDeliveryReport or an error
     */
    suspend fun processDeliveryReport(rawPayload: String): Result<MessageDeliveryReport>
}