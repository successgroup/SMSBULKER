package com.gscube.smsbulker.repository.impl

import android.util.Log
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.MessageAnalyticsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MessageAnalyticsRepo"

@Singleton
@ContributesBinding(AppScope::class)
class MessageAnalyticsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : MessageAnalyticsRepository {

    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: throw SecurityException("User not authenticated")
    }

    override suspend fun trackMessage(
        messageId: String,
        batchId: String,
        recipient: String,
        sender: String,
        messageLength: Int,
        messageSegments: Int,
        creditsUsed: Int
    ): Result<MessageAnalytics> {
        return try {
            val userId = getCurrentUserId()
            
            val messageAnalytics = MessageAnalytics(
                id = messageId,
                batchId = batchId,
                messageId = messageId,
                recipient = recipient,
                sender = sender,
                status = SmsStatus.PENDING,
                sentTimestamp = System.currentTimeMillis(),
                messageLength = messageLength,
                messageSegments = messageSegments,
                creditsUsed = creditsUsed,
                countryCode = extractCountryCode(recipient)
            )
            
            // Store in Firestore
            firestore.collection("users")
                .document(userId)
                .collection("message_analytics")
                .document(messageId)
                .set(messageAnalytics)
                .await()
                
            // Also add to batch messages collection for easier querying
            firestore.collection("users")
                .document(userId)
                .collection("batch_analytics")
                .document(batchId)
                .collection("messages")
                .document(messageId)
                .set(messageAnalytics)
                .await()
                
            Result.success(messageAnalytics)
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking message: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun trackBatch(
        batchId: String,
        totalMessages: Int,
        messageTemplate: String?,
        isPersonalized: Boolean,
        totalCreditsUsed: Int
    ): Result<BatchAnalytics> {
        return try {
            val userId = getCurrentUserId()
            
            val batchAnalytics = BatchAnalytics(
                id = batchId,
                batchId = batchId,
                userId = userId,
                totalMessages = totalMessages,
                pendingMessages = totalMessages, // Initially all messages are pending
                sentTimestamp = System.currentTimeMillis(),
                totalCreditsUsed = totalCreditsUsed,
                messageTemplate = messageTemplate,
                isPersonalized = isPersonalized
            )
            
            // Store in Firestore
            firestore.collection("users")
                .document(userId)
                .collection("batch_analytics")
                .document(batchId)
                .set(batchAnalytics)
                .await()
                
            // Update time-based analytics
            updateTimeBasedAnalytics(batchAnalytics)
                
            Result.success(batchAnalytics)
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking batch: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateMessageStatus(report: MessageDeliveryReport): Result<MessageAnalytics> {
        return try {
            val userId = getCurrentUserId()
            
            // Get current message analytics
            val messageRef = firestore.collection("users")
                .document(userId)
                .collection("message_analytics")
                .document(report.messageId)
                
            val messageSnapshot = messageRef.get().await()
            if (!messageSnapshot.exists()) {
                return Result.failure(Exception("Message analytics not found for ID: ${report.messageId}"))
            }
            
            val currentAnalytics = messageSnapshot.toObject(MessageAnalytics::class.java)!!
            val batchId = currentAnalytics.batchId
            
            // Update message analytics based on the report
            val updatedAnalytics = when (report.status) {
                SmsStatus.DELIVERED -> {
                    val deliveredTime = report.timestamp
                    val deliveryLatency = if (currentAnalytics.sentTimestamp > 0) {
                        deliveredTime - currentAnalytics.sentTimestamp
                    } else null
                    
                    currentAnalytics.copy(
                        status = SmsStatus.DELIVERED,
                        deliveredTimestamp = deliveredTime,
                        deliveryLatency = deliveryLatency,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                SmsStatus.FAILED -> {
                    currentAnalytics.copy(
                        status = SmsStatus.FAILED,
                        failedTimestamp = report.timestamp,
                        errorCode = report.errorCode,
                        errorMessage = report.errorMessage,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                else -> {
                    currentAnalytics.copy(
                        status = report.status,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            }
            
            // Update in Firestore (both in message_analytics and in batch_analytics/messages)
            firestore.runTransaction { transaction ->
                // Update in message_analytics
                transaction.set(messageRef, updatedAnalytics)
                
                // Update in batch_analytics/messages
                if (batchId.isNotEmpty()) {
                    val batchMessageRef = firestore.collection("users")
                        .document(userId)
                        .collection("batch_analytics")
                        .document(batchId)
                        .collection("messages")
                        .document(report.messageId)
                        
                    transaction.set(batchMessageRef, updatedAnalytics)
                }
            }.await()
            
            // Update batch status
            if (batchId.isNotEmpty()) {
                updateBatchStatus(batchId)
            }
            
            // Track error if failed
            if (report.status == SmsStatus.FAILED && report.errorCode != null) {
                trackError(report.errorCode, report.errorMessage ?: "Unknown error", report.recipient)
            }
            
            Result.success(updatedAnalytics)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating message status: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun updateMessageStatus(
        messageId: String,
        status: SmsStatus,
        errorCode: String?,
        errorMessage: String?
    ): Result<MessageAnalytics> {
        // Create a delivery report and use the existing method
        val report = MessageDeliveryReport(
            messageId = messageId,
            recipient = "", // Will be filled from existing data
            status = status,
            timestamp = System.currentTimeMillis(),
            errorCode = errorCode,
            errorMessage = errorMessage
        )
        
        return updateMessageStatus(report)
    }

    override suspend fun updateBatchStatus(batchId: String): Result<BatchAnalytics> {
        return try {
            val userId = getCurrentUserId()
            
            // Get current batch analytics
            val batchRef = firestore.collection("users")
                .document(userId)
                .collection("batch_analytics")
                .document(batchId)
                
            val batchSnapshot = batchRef.get().await()
            if (!batchSnapshot.exists()) {
                return Result.failure(Exception("Batch analytics not found for ID: $batchId"))
            }
            
            // Get all messages for this batch
            val messagesSnapshot = firestore.collection("users")
                .document(userId)
                .collection("batch_analytics")
                .document(batchId)
                .collection("messages")
                .get()
                .await()
                
            val messages = messagesSnapshot.toObjects(MessageAnalytics::class.java)
            
            // Calculate batch statistics
            val deliveredCount = messages.count { it.status == SmsStatus.DELIVERED }
            val failedCount = messages.count { it.status == SmsStatus.FAILED }
            val pendingCount = messages.count { it.status == SmsStatus.PENDING }
            
            // Calculate average delivery latency
            val deliveryLatencies = messages
                .filter { it.deliveryLatency != null }
                .mapNotNull { it.deliveryLatency }
            
            val avgDeliveryLatency = if (deliveryLatencies.isNotEmpty()) {
                deliveryLatencies.average().toLong()
            } else null
            
            // Determine if batch is completed
            val isCompleted = pendingCount == 0
            val completedTimestamp = if (isCompleted) System.currentTimeMillis() else null
            
            // Update batch analytics
            val currentBatch = batchSnapshot.toObject(BatchAnalytics::class.java)!!
            val updatedBatch = currentBatch.copy(
                deliveredMessages = deliveredCount,
                failedMessages = failedCount,
                pendingMessages = pendingCount,
                completedTimestamp = completedTimestamp ?: currentBatch.completedTimestamp,
                averageDeliveryLatency = avgDeliveryLatency,
                lastUpdated = System.currentTimeMillis()
            )
            
            // Update in Firestore
            batchRef.set(updatedBatch).await()
            
            // Update time-based analytics if batch is completed
            if (isCompleted && currentBatch.completedTimestamp == null) {
                updateTimeBasedAnalytics(updatedBatch)
            }
            
            Result.success(updatedBatch)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating batch status: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getMessageAnalytics(messageId: String): Result<MessageAnalytics> {
        return try {
            val userId = getCurrentUserId()
            
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("message_analytics")
                .document(messageId)
                .get()
                .await()
                
            if (!snapshot.exists()) {
                Result.failure(Exception("Message analytics not found for ID: $messageId"))
            } else {
                Result.success(snapshot.toObject(MessageAnalytics::class.java)!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting message analytics: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getBatchAnalytics(batchId: String): Result<BatchAnalytics> {
        return try {
            val userId = getCurrentUserId()
            
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("batch_analytics")
                .document(batchId)
                .get()
                .await()
                
            if (!snapshot.exists()) {
                Result.failure(Exception("Batch analytics not found for ID: $batchId"))
            } else {
                Result.success(snapshot.toObject(BatchAnalytics::class.java)!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting batch analytics: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getMessagesForBatch(batchId: String): Result<List<MessageAnalytics>> {
        return try {
            val userId = getCurrentUserId()
            
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("batch_analytics")
                .document(batchId)
                .collection("messages")
                .get()
                .await()
                
            val messages = snapshot.toObjects(MessageAnalytics::class.java)
            Result.success(messages)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting messages for batch: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getBatchesByTimeRange(startTime: Long, endTime: Long): Result<List<BatchAnalytics>> {
        return try {
            val userId = getCurrentUserId()
            
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("batch_analytics")
                .whereGreaterThanOrEqualTo("sentTimestamp", startTime)
                .whereLessThanOrEqualTo("sentTimestamp", endTime)
                .orderBy("sentTimestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                
            val batches = snapshot.toObjects(BatchAnalytics::class.java)
            Result.success(batches)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting batches by time range: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getTimeBasedAnalytics(
        period: AnalyticsPeriod,
        startTime: Long,
        endTime: Long
    ): Result<List<TimeBasedAnalytics>> {
        return try {
            val userId = getCurrentUserId()
            
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("time_analytics")
                .whereEqualTo("period", period.name)
                .whereGreaterThanOrEqualTo("startTime", startTime)
                .whereLessThanOrEqualTo("endTime", endTime)
                .orderBy("startTime", Query.Direction.ASCENDING)
                .get()
                .await()
                
            val analytics = snapshot.toObjects(TimeBasedAnalytics::class.java)
            Result.success(analytics)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting time-based analytics: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getErrorAnalytics(startTime: Long, endTime: Long): Result<List<ErrorAnalytics>> {
        return try {
            val userId = getCurrentUserId()
            
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("error_analytics")
                .whereGreaterThanOrEqualTo("lastOccurrence", startTime)
                .whereLessThanOrEqualTo("lastOccurrence", endTime)
                .orderBy("lastOccurrence", Query.Direction.DESCENDING)
                .get()
                .await()
                
            val errors = snapshot.toObjects(ErrorAnalytics::class.java)
            Result.success(errors)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting error analytics: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun exportBatchAnalyticsAsCsv(batchId: String): Result<String> {
        return try {
            val batchResult = getBatchAnalytics(batchId)
            if (batchResult.isFailure) {
                return Result.failure(batchResult.exceptionOrNull()!!)
            }
            
            val batch = batchResult.getOrNull()!!
            
            val messagesResult = getMessagesForBatch(batchId)
            if (messagesResult.isFailure) {
                return Result.failure(messagesResult.exceptionOrNull()!!)
            }
            
            val messages = messagesResult.getOrNull()!!
            
            val writer = StringWriter()
            writer.use { w ->
                // Write batch summary
                w.write("Batch Summary\n")
                w.write("Batch ID,${batch.batchId}\n")
                w.write("Total Messages,${batch.totalMessages}\n")
                w.write("Delivered Messages,${batch.deliveredMessages}\n")
                w.write("Failed Messages,${batch.failedMessages}\n")
                w.write("Pending Messages,${batch.pendingMessages}\n")
                w.write("Delivery Rate,${String.format("%.2f%%", batch.deliveryRate * 100)}\n")
                w.write("Failure Rate,${String.format("%.2f%%", batch.failureRate * 100)}\n")
                w.write("Sent Time,${formatTimestamp(batch.sentTimestamp)}\n")
                batch.completedTimestamp?.let {
                    w.write("Completed Time,${formatTimestamp(it)}\n")
                }
                batch.averageDeliveryLatency?.let {
                    w.write("Average Delivery Latency,${formatLatency(it)}\n")
                }
                w.write("Total Credits Used,${batch.totalCreditsUsed}\n")
                w.write("Status,${batch.status}\n\n")
                
                // Write message details
                w.write("Message Details\n")
                w.write("Message ID,Recipient,Status,Sent Time,Delivered Time,Failed Time,Delivery Latency,Message Length,Message Segments,Credits Used,Error Code,Error Message\n")
                
                for (message in messages) {
                    w.write("${message.messageId},")
                    w.write("${message.recipient},")
                    w.write("${message.status},")
                    w.write("${formatTimestamp(message.sentTimestamp)},")
                    w.write("${message.deliveredTimestamp?.let { formatTimestamp(it) } ?: ""},")
                    w.write("${message.failedTimestamp?.let { formatTimestamp(it) } ?: ""},")
                    w.write("${message.deliveryLatency?.let { formatLatency(it) } ?: ""},")
                    w.write("${message.messageLength},")
                    w.write("${message.messageSegments},")
                    w.write("${message.creditsUsed},")
                    w.write("${message.errorCode ?: ""},")
                    w.write("${message.errorMessage?.replace(",", ";") ?: ""}\n")
                }
            }
            
            Result.success(writer.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting batch analytics as CSV: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun exportTimeBasedAnalyticsAsCsv(
        period: AnalyticsPeriod,
        startTime: Long,
        endTime: Long
    ): Result<String> {
        return try {
            val analyticsResult = getTimeBasedAnalytics(period, startTime, endTime)
            if (analyticsResult.isFailure) {
                return Result.failure(analyticsResult.exceptionOrNull()!!)
            }
            
            val analytics = analyticsResult.getOrNull()!!
            
            val writer = StringWriter()
            writer.use { w ->
                // Write header
                w.write("Time Period,Start Time,End Time,Total Messages,Delivered Messages,Failed Messages,Pending Messages,")
                w.write("Delivery Rate,Failure Rate,Total Batches,Completed Batches,Failed Batches,Partial Batches,")
                w.write("Pending Batches,Batch Success Rate,Total Credits Used,Average Delivery Latency\n")
                
                // Write data rows
                for (item in analytics) {
                    val periodLabel = when (period) {
                        AnalyticsPeriod.DAILY -> formatDate(item.startTime)
                        AnalyticsPeriod.WEEKLY -> "Week of ${formatDate(item.startTime)}"
                        AnalyticsPeriod.MONTHLY -> formatMonth(item.startTime)
                        AnalyticsPeriod.YEARLY -> formatYear(item.startTime)
                        else -> "${formatDate(item.startTime)} - ${formatDate(item.endTime)}"
                    }
                    
                    w.write("$periodLabel,")
                    w.write("${formatTimestamp(item.startTime)},")
                    w.write("${formatTimestamp(item.endTime)},")
                    w.write("${item.totalMessages},")
                    w.write("${item.deliveredMessages},")
                    w.write("${item.failedMessages},")
                    w.write("${item.pendingMessages},")
                    w.write("${String.format("%.2f%%", item.deliveryRate * 100)},")
                    w.write("${String.format("%.2f%%", item.failureRate * 100)},")
                    w.write("${item.totalBatches},")
                    w.write("${item.completedBatches},")
                    w.write("${item.failedBatches},")
                    w.write("${item.partialBatches},")
                    w.write("${item.pendingBatches},")
                    w.write("${String.format("%.2f%%", item.batchSuccessRate * 100)},")
                    w.write("${item.totalCreditsUsed},")
                    w.write("${item.averageDeliveryLatency?.let { formatLatency(it) } ?: ""}\n")
                }
            }
            
            Result.success(writer.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting time-based analytics as CSV: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getDeliveryRate(startTime: Long, endTime: Long): Result<Double> {
        return try {
            val userId = getCurrentUserId()
            
            // Get all batches in the time range
            val batches = firestore.collection("users")
                .document(userId)
                .collection("batch_analytics")
                .whereGreaterThanOrEqualTo("sentTimestamp", startTime)
                .whereLessThanOrEqualTo("sentTimestamp", endTime)
                .get()
                .await()
                .toObjects(BatchAnalytics::class.java)
                
            if (batches.isEmpty()) {
                return Result.success(0.0)
            }
            
            val totalMessages = batches.sumOf { it.totalMessages }
            val deliveredMessages = batches.sumOf { it.deliveredMessages }
            
            val deliveryRate = if (totalMessages > 0) {
                deliveredMessages.toDouble() / totalMessages
            } else 0.0
            
            Result.success(deliveryRate)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting delivery rate: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getAverageDeliveryLatency(startTime: Long, endTime: Long): Result<Long> {
        return try {
            val userId = getCurrentUserId()
            
            // Get all batches in the time range
            val batches = firestore.collection("users")
                .document(userId)
                .collection("batch_analytics")
                .whereGreaterThanOrEqualTo("sentTimestamp", startTime)
                .whereLessThanOrEqualTo("sentTimestamp", endTime)
                .get()
                .await()
                .toObjects(BatchAnalytics::class.java)
                
            if (batches.isEmpty()) {
                return Result.success(0L)
            }
            
            // Calculate weighted average of delivery latencies
            var totalLatency = 0.0
            var totalDeliveredMessages = 0
            
            for (batch in batches) {
                batch.averageDeliveryLatency?.let { latency ->
                    totalLatency += latency * batch.deliveredMessages
                    totalDeliveredMessages += batch.deliveredMessages
                }
            }
            
            val avgLatency = if (totalDeliveredMessages > 0) {
                (totalLatency / totalDeliveredMessages).toLong()
            } else 0L
            
            Result.success(avgLatency)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting average delivery latency: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getTotalCreditsUsed(startTime: Long, endTime: Long): Result<Int> {
        return try {
            val userId = getCurrentUserId()
            
            // Get all batches in the time range
            val batches = firestore.collection("users")
                .document(userId)
                .collection("batch_analytics")
                .whereGreaterThanOrEqualTo("sentTimestamp", startTime)
                .whereLessThanOrEqualTo("sentTimestamp", endTime)
                .get()
                .await()
                .toObjects(BatchAnalytics::class.java)
                
            val totalCredits = batches.sumOf { it.totalCreditsUsed }
            
            Result.success(totalCredits)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total credits used: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getMessageVolume(startTime: Long, endTime: Long): Result<Int> {
        return try {
            val userId = getCurrentUserId()
            
            // Get all batches in the time range
            val batches = firestore.collection("users")
                .document(userId)
                .collection("batch_analytics")
                .whereGreaterThanOrEqualTo("sentTimestamp", startTime)
                .whereLessThanOrEqualTo("sentTimestamp", endTime)
                .get()
                .await()
                .toObjects(BatchAnalytics::class.java)
                
            val totalMessages = batches.sumOf { it.totalMessages }
            
            Result.success(totalMessages)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting message volume: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun observeMessageAnalytics(messageId: String): Flow<MessageAnalytics> = callbackFlow {
        val userId = getCurrentUserId()
        
        val registration = firestore.collection("users")
            .document(userId)
            .collection("message_analytics")
            .document(messageId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val analytics = snapshot.toObject(MessageAnalytics::class.java)!!
                    trySend(analytics)
                }
            }
            
        awaitClose { registration.remove() }
    }

    override fun observeBatchAnalytics(batchId: String): Flow<BatchAnalytics> = callbackFlow {
        val userId = getCurrentUserId()
        
        val registration = firestore.collection("users")
            .document(userId)
            .collection("batch_analytics")
            .document(batchId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val analytics = snapshot.toObject(BatchAnalytics::class.java)!!
                    trySend(analytics)
                }
            }
            
        awaitClose { registration.remove() }
    }

    override suspend fun processDeliveryReport(rawPayload: String): Result<MessageDeliveryReport> {
        return try {
            // Parse the raw payload
            val json = JSONObject(rawPayload)
            
            val messageId = json.optString("message_id")
            if (messageId.isEmpty()) {
                return Result.failure(Exception("Invalid delivery report: missing message_id"))
            }
            
            val recipient = json.optString("recipient")
            val statusStr = json.optString("status")
            val status = try {
                SmsStatus.fromString(statusStr)
            } catch (e: Exception) {
                SmsStatus.FAILED
            }
            
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())
            val errorCode = json.optString("error_code").takeIf { it.isNotEmpty() }
            val errorMessage = json.optString("error_message").takeIf { it.isNotEmpty() }
            
            val report = MessageDeliveryReport(
                id = UUID.randomUUID().toString(),
                messageId = messageId,
                recipient = recipient,
                status = status,
                timestamp = timestamp,
                errorCode = errorCode,
                errorMessage = errorMessage,
                rawPayload = rawPayload
            )
            
            // Store the report
            val userId = getCurrentUserId()
            firestore.collection("users")
                .document(userId)
                .collection("delivery_reports")
                .document(report.id)
                .set(report)
                .await()
                
            // Update message status
            updateMessageStatus(report)
            
            Result.success(report)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing delivery report: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Helper methods
    
    private suspend fun updateTimeBasedAnalytics(batch: BatchAnalytics) {
        try {
            val userId = getCurrentUserId()
            val now = System.currentTimeMillis()
            
            // Update daily analytics
            updatePeriodAnalytics(AnalyticsPeriod.DAILY, batch, userId, now)
            
            // Update weekly analytics
            updatePeriodAnalytics(AnalyticsPeriod.WEEKLY, batch, userId, now)
            
            // Update monthly analytics
            updatePeriodAnalytics(AnalyticsPeriod.MONTHLY, batch, userId, now)
            
            // Update yearly analytics
            updatePeriodAnalytics(AnalyticsPeriod.YEARLY, batch, userId, now)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating time-based analytics: ${e.message}", e)
        }
    }
    
    private suspend fun updatePeriodAnalytics(
        period: AnalyticsPeriod,
        batch: BatchAnalytics,
        userId: String,
        now: Long
    ) {
        val (startTime, endTime) = getPeriodTimeRange(period, now)
        val periodId = "${period.name.lowercase()}_${startTime}"
        
        val analyticsRef = firestore.collection("users")
            .document(userId)
            .collection("time_analytics")
            .document(periodId)
            
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(analyticsRef)
            
            if (snapshot.exists()) {
                // Update existing analytics
                val analytics = snapshot.toObject(TimeBasedAnalytics::class.java)!!
                
                val updatedAnalytics = analytics.copy(
                    totalMessages = analytics.totalMessages + batch.totalMessages,
                    deliveredMessages = analytics.deliveredMessages + batch.deliveredMessages,
                    failedMessages = analytics.failedMessages + batch.failedMessages,
                    pendingMessages = analytics.pendingMessages + batch.pendingMessages,
                    totalBatches = analytics.totalBatches + 1,
                    completedBatches = analytics.completedBatches + if (batch.status == BatchStatus.COMPLETED) 1 else 0,
                    failedBatches = analytics.failedBatches + if (batch.status == BatchStatus.FAILED) 1 else 0,
                    partialBatches = analytics.partialBatches + if (batch.status == BatchStatus.PARTIAL_SUCCESS) 1 else 0,
                    pendingBatches = analytics.pendingBatches + if (batch.status == BatchStatus.QUEUED || batch.status == BatchStatus.PROCESSING) 1 else 0,
                    totalCreditsUsed = analytics.totalCreditsUsed + batch.totalCreditsUsed,
                    lastUpdated = now
                )
                
                transaction.set(analyticsRef, updatedAnalytics)
            } else {
                // Create new analytics
                val analytics = TimeBasedAnalytics(
                    id = periodId,
                    userId = userId,
                    period = period,
                    startTime = startTime,
                    endTime = endTime,
                    totalMessages = batch.totalMessages,
                    deliveredMessages = batch.deliveredMessages,
                    failedMessages = batch.failedMessages,
                    pendingMessages = batch.pendingMessages,
                    totalBatches = 1,
                    completedBatches = if (batch.status == BatchStatus.COMPLETED) 1 else 0,
                    failedBatches = if (batch.status == BatchStatus.FAILED) 1 else 0,
                    partialBatches = if (batch.status == BatchStatus.PARTIAL_SUCCESS) 1 else 0,
                    pendingBatches = if (batch.status == BatchStatus.QUEUED || batch.status == BatchStatus.PROCESSING) 1 else 0,
                    totalCreditsUsed = batch.totalCreditsUsed,
                    averageDeliveryLatency = batch.averageDeliveryLatency,
                    lastUpdated = now
                )
                
                transaction.set(analyticsRef, analytics)
            }
        }.await()
    }
    
    private fun getPeriodTimeRange(period: AnalyticsPeriod, timestamp: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val startCalendar = calendar.clone() as Calendar
        val endCalendar = calendar.clone() as Calendar
        
        when (period) {
            AnalyticsPeriod.DAILY -> {
                // Start is beginning of day, end is end of day
                endCalendar.add(Calendar.DAY_OF_MONTH, 1)
                endCalendar.add(Calendar.MILLISECOND, -1)
            }
            AnalyticsPeriod.WEEKLY -> {
                // Start is beginning of week (Sunday), end is end of week (Saturday)
                startCalendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                endCalendar.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
                endCalendar.set(Calendar.HOUR_OF_DAY, 23)
                endCalendar.set(Calendar.MINUTE, 59)
                endCalendar.set(Calendar.SECOND, 59)
                endCalendar.set(Calendar.MILLISECOND, 999)
            }
            AnalyticsPeriod.MONTHLY -> {
                // Start is beginning of month, end is end of month
                startCalendar.set(Calendar.DAY_OF_MONTH, 1)
                endCalendar.set(Calendar.DAY_OF_MONTH, endCalendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                endCalendar.set(Calendar.HOUR_OF_DAY, 23)
                endCalendar.set(Calendar.MINUTE, 59)
                endCalendar.set(Calendar.SECOND, 59)
                endCalendar.set(Calendar.MILLISECOND, 999)
            }
            AnalyticsPeriod.YEARLY -> {
                // Start is beginning of year, end is end of year
                startCalendar.set(Calendar.DAY_OF_YEAR, 1)
                endCalendar.set(Calendar.DAY_OF_YEAR, endCalendar.getActualMaximum(Calendar.DAY_OF_YEAR))
                endCalendar.set(Calendar.HOUR_OF_DAY, 23)
                endCalendar.set(Calendar.MINUTE, 59)
                endCalendar.set(Calendar.SECOND, 59)
                endCalendar.set(Calendar.MILLISECOND, 999)
            }
            else -> {
                // For custom period, use the same day
                endCalendar.add(Calendar.DAY_OF_MONTH, 1)
                endCalendar.add(Calendar.MILLISECOND, -1)
            }
        }
        
        return Pair(startCalendar.timeInMillis, endCalendar.timeInMillis)
    }
    
    private suspend fun trackError(errorCode: String, errorMessage: String, recipient: String) {
        try {
            val userId = getCurrentUserId()
            val now = System.currentTimeMillis()
            
            val errorRef = firestore.collection("users")
                .document(userId)
                .collection("error_analytics")
                .document(errorCode)
                
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(errorRef)
                
                if (snapshot.exists()) {
                    // Update existing error analytics
                    val errorAnalytics = snapshot.toObject(ErrorAnalytics::class.java)!!
                    
                    // Add recipient to affected recipients (limit to 100)
                    val affectedRecipients = errorAnalytics.affectedRecipients.toMutableList()
                    if (recipient.isNotEmpty() && !affectedRecipients.contains(recipient) && affectedRecipients.size < 100) {
                        affectedRecipients.add(recipient)
                    }
                    
                    val updatedError = errorAnalytics.copy(
                        count = errorAnalytics.count + 1,
                        lastOccurrence = now,
                        affectedRecipients = affectedRecipients,
                        lastUpdated = now
                    )
                    
                    transaction.set(errorRef, updatedError)
                } else {
                    // Create new error analytics
                    val affectedRecipients = if (recipient.isNotEmpty()) listOf(recipient) else emptyList()
                    
                    val errorAnalytics = ErrorAnalytics(
                        id = errorCode,
                        userId = userId,
                        errorCode = errorCode,
                        errorMessage = errorMessage,
                        count = 1,
                        firstOccurrence = now,
                        lastOccurrence = now,
                        affectedRecipients = affectedRecipients,
                        lastUpdated = now
                    )
                    
                    transaction.set(errorRef, errorAnalytics)
                }
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking error: ${e.message}", e)
        }
    }
    
    private fun extractCountryCode(phoneNumber: String): String? {
        // Simple extraction of country code from phone number
        // This is a basic implementation and might need to be enhanced
        return when {
            phoneNumber.startsWith("+") -> {
                val endIndex = minOf(4, phoneNumber.length)
                phoneNumber.substring(0, endIndex)
            }
            else -> null
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    private fun formatDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    private fun formatMonth(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    private fun formatYear(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    private fun formatLatency(latency: Long): String {
        return when {
            latency < 1000 -> "${latency}ms"
            latency < 60000 -> String.format("%.2fs", latency / 1000.0)
            else -> String.format("%.2fm", latency / 60000.0)
        }
    }
}