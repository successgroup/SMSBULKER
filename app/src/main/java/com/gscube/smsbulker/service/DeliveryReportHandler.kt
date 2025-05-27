package com.gscube.smsbulker.service

import com.gscube.smsbulker.data.FailureAnalysis
import com.gscube.smsbulker.data.model.DeliveryReport
import com.gscube.smsbulker.repository.AnalyticsRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.auth.FirebaseAuth

@Singleton
class DeliveryReportHandler @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth // Add Firebase Auth
) {
    
    suspend fun handleDeliveryReport(report: DeliveryReport) {
        val userId = auth.currentUser?.uid ?: return // Skip if no user authenticated
        
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
        val currentFailures = snapshot.toObject(FailureAnalysis::class.java) ?: FailureAnalysis()
        
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
        val totalFailures = firestore.collection("analytics").document("summary")
            .get().await().getLong("failedMessages") ?: 0
        
        if (totalFailures > 0) {
            val percentage = updatedFailures.count.toDouble() / totalFailures.toDouble()
            failuresRef.set(updatedFailures.copy(percentage = percentage)).await()
        } else {
            failuresRef.set(updatedFailures).await()
        }
    }
}