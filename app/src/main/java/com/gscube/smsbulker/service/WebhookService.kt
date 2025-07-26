package com.gscube.smsbulker.service

import com.gscube.smsbulker.data.model.DeliveryReport
import com.gscube.smsbulker.repository.MessageAnalyticsRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

class WebhookService : FirebaseMessagingService() {
    
    @Inject
    lateinit var deliveryReportHandler: DeliveryReportHandler
    
    @Inject
    lateinit var messageAnalyticsRepository: MessageAnalyticsRepository
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Check if the message contains delivery report data
        val data = remoteMessage.data
        if (data.containsKey("message_id") && data.containsKey("status")) {
            val deliveryReport = DeliveryReport(
                messageId = data["message_id"] ?: "",
                recipient = data["recipient"] ?: "",
                status = data["status"] ?: "",
                timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
                errorCode = data["error_code"],
                errorMessage = data["error_message"]
            )
            
            CoroutineScope(Dispatchers.IO).launch {
                // Process with legacy handler for backward compatibility
                deliveryReportHandler.handleDeliveryReport(deliveryReport)
                
                // Process with new analytics system directly
                val rawPayload = createRawPayload(data)
                messageAnalyticsRepository.processDeliveryReport(rawPayload)
            }
        }
    }
    
    /**
     * Create a raw JSON payload from the FCM data
     */
    private fun createRawPayload(data: Map<String, String>): String {
        val json = JSONObject()
        for ((key, value) in data) {
            json.put(key, value)
        }
        return json.toString()
    }
}