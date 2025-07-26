package com.gscube.smsbulker.service

import com.gscube.smsbulker.data.FailureAnalysis
import com.gscube.smsbulker.data.model.DeliveryReport
import com.gscube.smsbulker.data.model.MessageDeliveryReport
import com.gscube.smsbulker.data.model.SmsStatus
import com.gscube.smsbulker.repository.AnalyticsRepository
import com.gscube.smsbulker.repository.MessageAnalyticsRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.auth.FirebaseAuth

@Singleton
class DeliveryReportHandler @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val messageAnalyticsRepository: MessageAnalyticsRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    
    suspend fun handleDeliveryReport(report: DeliveryReport) {
        val userId = auth.currentUser?.uid ?: return // Skip if no user authenticated
        
        // Update legacy analytics system
        when (report.status.lowercase()) {
            "delivered" -> {
                analyticsRepository.updateAnalytics(messagesSent = 0, messagesFailed = 0)
                incrementDeliveredCount(userId)
            }
            "failed" -> {
                analyticsRepository.updateAnalytics(messagesSent = 0, messagesFailed = 1)
                updateFailureAnalysis(userId, report.errorCode ?: "unknown", report.errorMessage ?: "Unknown error")
            }
        }
        
        // Convert to MessageDeliveryReport and process with new analytics system
        val status = SmsStatus.fromString(report.status)
        val messageDeliveryReport = MessageDeliveryReport(
            messageId = report.messageId,
            recipient = report.recipient,
            status = status,
            timestamp = report.timestamp,
            errorCode = report.errorCode,
            errorMessage = report.errorMessage,
            rawPayload = convertReportToJson(report)
        )
        
        // Update message status in the new analytics system
        messageAnalyticsRepository.updateMessageStatus(messageDeliveryReport)
    }
    
    private suspend fun incrementDeliveredCount(userId: String) {
        val summaryRef = firestore.collection("users")
            .document(userId)
            .collection("analytics")
            .document("summary")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(summaryRef)
            val currentDelivered = snapshot.getLong("deliveredMessages") ?: 0
            transaction.update(summaryRef, "deliveredMessages", currentDelivered + 1)
        }.await()
    }
    
    private suspend fun updateFailureAnalysis(userId: String, errorCode: String, errorMessage: String) {
        val failureReason = "$errorCode: $errorMessage"
        val failuresRef = firestore.collection("users")
            .document(userId)
            .collection("analytics")
            .document("failures")
        
        // Get current failures
        val snapshot = failuresRef.get().await()
        val currentFailures = snapshot.toObject(FailureAnalysis::class.java) ?: FailureAnalysis(reason = failureReason, count = 0, percentage = 0.0)
        
        // Update or add the failure reason
        val updatedFailures = if (currentFailures.reason == failureReason) {
            currentFailures.copy(count = currentFailures.count + 1)
        } else {
            // Create a new failure analysis entry
            FailureAnalysis(
                reason = failureReason,
                count = 1,
                percentage = 0.0 // Will be calculated later
            )
        }
        
        // Calculate percentage
        val totalFailuresRef = firestore.collection("users")
            .document(userId)
            .collection("analytics")
            .document("summary")
        val totalFailures = totalFailuresRef.get().await().getLong("failedMessages") ?: 0
        
        if (totalFailures > 0) {
            val percentage = updatedFailures.count.toDouble() / totalFailures.toDouble()
            failuresRef.set(updatedFailures.copy(percentage = percentage)).await()
        } else {
            failuresRef.set(updatedFailures).await()
        }
    }
    
    /**
     * Convert a DeliveryReport to a JSON string for storage in the raw payload field
     */
    private fun convertReportToJson(report: DeliveryReport): String {
        val json = JSONObject()
        json.put("message_id", report.messageId)
        json.put("recipient", report.recipient)
        json.put("status", report.status)
        json.put("timestamp", report.timestamp)
        report.errorCode?.let { json.put("error_code", it) }
        report.errorMessage?.let { json.put("error_message", it) }
        return json.toString()
    }
}