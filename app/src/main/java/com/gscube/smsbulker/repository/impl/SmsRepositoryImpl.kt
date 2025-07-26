package com.gscube.smsbulker.repository.impl

import android.util.Log
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.data.network.ArkeselApi
import com.gscube.smsbulker.repository.SmsRepository
import com.gscube.smsbulker.repository.MessageAnalyticsRepository
import com.gscube.smsbulker.di.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

@Singleton
@ContributesBinding(AppScope::class)
class SmsRepositoryImpl @Inject constructor(
    private val arkeselApi: ArkeselApi,
    @Named("sandbox_mode") private val sandboxMode: Boolean = false,
    private val messageAnalyticsRepository: MessageAnalyticsRepository
) : SmsRepository {

    private fun convertToArkeselTemplate(template: String): String {
        val regex = Regex("\\{(\\w+)\\}")
        return template.replace(regex) { matchResult ->
            val variableName = matchResult.groupValues[1]
            "<%$variableName%>"
        }
    }

    override suspend fun sendSingleSms(
        recipient: String,
        message: String,
        sender: String?
    ): Flow<BulkSmsResult> = flow {
        val recipientMap = mapOf(
            "number" to recipient,
            "name" to recipient
        )

        // For single SMS - Changed from ArkeselSmsRequest to ArkeselRegularSmsRequest
        val regularSmsRequest = ArkeselRegularSmsRequest(
            sender = sender ?: "SMSBULKER",
            message = if (message.contains("{")) {
                convertToArkeselTemplate(message)
            } else {
                message
            },
            recipients = listOf(recipient), // Changed from Map to List
            sandbox = sandboxMode,
            callbackUrl = "https://your-firebase-function-url.com/webhook/delivery-report"
        )

        // Inside the SmsRepositoryImpl class, update the request creation:

        // For single SMS
        val request = ArkeselSmsRequest(
            sender = sender ?: "SMSBULKER",
            message = if (message.contains("{")) {
                convertToArkeselTemplate(message)
            } else {
                message
            },
            recipients = mapOf(
                recipient to recipientMap
            ),
            sandbox = sandboxMode,
            callbackUrl = "https://your-firebase-function-url.com/webhook/delivery-report" // Add this line
        )

        // Generate a batch ID for analytics
        val batchId = UUID.randomUUID().toString()
        
        // Calculate message length and segments for analytics
        val messageLength = message.length
        val messageSegments = if (messageLength == 0) 0 else ((messageLength - 1) / 152) + 1
        
        // Track batch in analytics
        messageAnalyticsRepository.trackBatch(
            batchId = batchId,
            totalMessages = 1,
            messageTemplate = message,
            isPersonalized = false,
            totalCreditsUsed = messageSegments
        )

        // Similarly update the bulk SMS and personalized SMS methods
        Log.d("SmsRepository", "Sending SMS request: $request")
        val response = arkeselApi.sendSms(regularSmsRequest)
        Log.d("SmsRepository", "SMS response code: ${response.code()}")
        Log.d("SmsRepository", "SMS response body: ${response.body()}")
        Log.d("SmsRepository", "SMS error body: ${response.errorBody()?.string()}")

        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            if (responseBody.status == "success" && responseBody.data.isNotEmpty()) {
                val result = responseBody.data.first()
                
                // Track message in analytics
                messageAnalyticsRepository.trackMessage(
                    messageId = result.id,
                    batchId = batchId,
                    recipient = result.recipient,
                    sender = sender ?: "SMSBULKER",
                    messageLength = messageLength,
                    messageSegments = messageSegments,
                    creditsUsed = messageSegments
                )
                
                // Update message status to delivered
                messageAnalyticsRepository.updateMessageStatus(
                    messageId = result.id,
                    status = SmsStatus.DELIVERED
                )
                
                emit(BulkSmsResult(
                    messageId = result.id,
                    recipient = result.recipient,
                    status = "DELIVERED",
                    timestamp = System.currentTimeMillis()
                ))
            } else {
                val messageId = UUID.randomUUID().toString()
                
                // Track failed message in analytics
                messageAnalyticsRepository.trackMessage(
                    messageId = messageId,
                    batchId = batchId,
                    recipient = recipient,
                    sender = sender ?: "SMSBULKER",
                    messageLength = messageLength,
                    messageSegments = messageSegments,
                    creditsUsed = 0 // No credits used for failed messages
                )
                
                // Update message status to failed
                messageAnalyticsRepository.updateMessageStatus(
                    messageId = messageId,
                    status = SmsStatus.FAILED,
                    errorCode = "API_ERROR",
                    errorMessage = "API returned failure status: ${responseBody.status}"
                )
                
                emit(BulkSmsResult(
                    messageId = messageId,
                    recipient = recipient,
                    status = "FAILED",
                    timestamp = System.currentTimeMillis()
                ))
            }
        } else {
            val messageId = UUID.randomUUID().toString()
            
            // Track failed message in analytics
            messageAnalyticsRepository.trackMessage(
                messageId = messageId,
                batchId = batchId,
                recipient = recipient,
                sender = sender ?: "SMSBULKER",
                messageLength = messageLength,
                messageSegments = messageSegments,
                creditsUsed = 0 // No credits used for failed messages
            )
            
            // Update message status to failed
            messageAnalyticsRepository.updateMessageStatus(
                messageId = messageId,
                status = SmsStatus.FAILED,
                errorCode = "HTTP_ERROR",
                errorMessage = "HTTP Error ${response.code()}: ${response.message()}"
            )
            
            emit(BulkSmsResult(
                messageId = messageId,
                recipient = recipient,
                status = "FAILED",
                timestamp = System.currentTimeMillis()
            ))
        }
        
        // Update batch status
        messageAnalyticsRepository.updateBatchStatus(batchId)
    }

    override suspend fun sendBulkSms(
        recipients: List<String>,
        message: String,
        sender: String?
    ): Flow<List<BulkSmsResult>> = flow {
        // No need for recipientsMap for ArkeselRegularSmsRequest

        val request = ArkeselRegularSmsRequest(
            sender = sender ?: "GSCube",
            message = if (message.contains("{")) {
                convertToArkeselTemplate(message)
            } else {
                message
            },
            recipients = recipients, // Use the list directly
            sandbox = sandboxMode
        )

        // Generate a batch ID for analytics
        val batchId = UUID.randomUUID().toString()
        
        // Calculate message length and segments for analytics
        val messageLength = message.length
        val messageSegments = if (messageLength == 0) 0 else ((messageLength - 1) / 152) + 1
        val totalCreditsRequired = messageSegments * recipients.size
        
        // Track batch in analytics
        messageAnalyticsRepository.trackBatch(
            batchId = batchId,
            totalMessages = recipients.size,
            messageTemplate = message,
            isPersonalized = false,
            totalCreditsUsed = totalCreditsRequired
        )

        Log.d("SmsRepository", "Sending bulk SMS request: $request")
        val response = arkeselApi.sendSms(request)
        Log.d("SmsRepository", "Bulk SMS response code: ${response.code()}")
        Log.d("SmsRepository", "Bulk SMS response body: ${response.body()}")
        Log.d("SmsRepository", "Bulk SMS error body: ${response.errorBody()?.string()}")

        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            if (responseBody.status == "success") {
                // Create a map of recipient to message ID for quick lookup
                val recipientToMessageId = responseBody.data.associate {
                    it.recipient to it.id
                }

                val results = recipients.map { recipient ->
                    val messageId = recipientToMessageId[recipient] ?: UUID.randomUUID().toString()
                    val isDelivered = recipientToMessageId.containsKey(recipient)
                    
                    // Track message in analytics
                    messageAnalyticsRepository.trackMessage(
                        messageId = messageId,
                        batchId = batchId,
                        recipient = recipient,
                        sender = sender ?: "GSCube",
                        messageLength = messageLength,
                        messageSegments = messageSegments,
                        creditsUsed = if (isDelivered) messageSegments else 0
                    )
                    
                    // Update message status
                    if (isDelivered) {
                        messageAnalyticsRepository.updateMessageStatus(
                            messageId = messageId,
                            status = SmsStatus.DELIVERED
                        )
                    } else {
                        messageAnalyticsRepository.updateMessageStatus(
                            messageId = messageId,
                            status = SmsStatus.FAILED,
                            errorCode = "RECIPIENT_NOT_FOUND",
                            errorMessage = "Recipient not found in API response"
                        )
                    }
                    
                    BulkSmsResult(
                        messageId = messageId,
                        recipient = recipient,
                        status = if (isDelivered) "DELIVERED" else "FAILED",
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                emit(results)
            } else {
                val results = recipients.map { recipient ->
                    val messageId = UUID.randomUUID().toString()
                    
                    // Track failed message in analytics
                    messageAnalyticsRepository.trackMessage(
                        messageId = messageId,
                        batchId = batchId,
                        recipient = recipient,
                        sender = sender ?: "GSCube",
                        messageLength = messageLength,
                        messageSegments = messageSegments,
                        creditsUsed = 0 // No credits used for failed messages
                    )
                    
                    // Update message status to failed
                    messageAnalyticsRepository.updateMessageStatus(
                        messageId = messageId,
                        status = SmsStatus.FAILED,
                        errorCode = "API_ERROR",
                        errorMessage = "API returned failure status: ${responseBody.status}"
                    )
                    
                    BulkSmsResult(
                        messageId = messageId,
                        recipient = recipient,
                        status = "FAILED",
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                emit(results)
            }
        } else {
            val results = recipients.map { recipient ->
                val messageId = UUID.randomUUID().toString()
                
                // Track failed message in analytics
                messageAnalyticsRepository.trackMessage(
                    messageId = messageId,
                    batchId = batchId,
                    recipient = recipient,
                    sender = sender ?: "GSCube",
                    messageLength = messageLength,
                    messageSegments = messageSegments,
                    creditsUsed = 0 // No credits used for failed messages
                )
                
                // Update message status to failed
                messageAnalyticsRepository.updateMessageStatus(
                    messageId = messageId,
                    status = SmsStatus.FAILED,
                    errorCode = "HTTP_ERROR",
                    errorMessage = "HTTP Error ${response.code()}: ${response.message()}"
                )
                
                BulkSmsResult(
                    messageId = messageId,
                    recipient = recipient,
                    status = "FAILED",
                    timestamp = System.currentTimeMillis()
                )
            }
            
            emit(results)
        }
        
        // Update batch status
        messageAnalyticsRepository.updateBatchStatus(batchId)
    }

    override suspend fun sendPersonalizedSms(
        recipients: List<Pair<String, Map<String, String>>>,
        messageTemplate: String,
        sender: String?
    ): Flow<List<BulkSmsResult>> = flow {
        val recipientsMap = recipients.associate { (phoneNumber, variables) ->
            phoneNumber to mapOf(
                "number" to phoneNumber,
                "name" to (variables["name"] ?: phoneNumber)
            ).plus(variables)
        }

        val request = ArkeselSmsRequest(
            sender = sender ?: "SMSBULKER",
            message = convertToArkeselTemplate(messageTemplate),
            recipients = recipientsMap,
            sandbox = sandboxMode
        )

        // Generate a batch ID for analytics
        val batchId = UUID.randomUUID().toString()
        
        // For personalized messages, we need to calculate credits for each recipient individually
        var totalCreditsRequired = 0
        val recipientMessageLengths = mutableMapOf<String, Int>()
        val recipientMessageSegments = mutableMapOf<String, Int>()
        
        // Calculate message length for each recipient after variable substitution
        for ((phoneNumber, variables) in recipients) {
            var personalizedMessage = messageTemplate
            
            // Replace {name} format
            val curlyBraceRegex = Regex("\\{(\\w+)\\}")
            personalizedMessage = personalizedMessage.replace(curlyBraceRegex) { matchResult ->
                val variableName = matchResult.groupValues[1]
                variables[variableName] ?: matchResult.value
            }
            
            val messageLength = personalizedMessage.length
            val messageSegments = if (messageLength == 0) 0 else ((messageLength - 1) / 152) + 1
            
            recipientMessageLengths[phoneNumber] = messageLength
            recipientMessageSegments[phoneNumber] = messageSegments
            totalCreditsRequired += messageSegments
        }
        
        // Track batch in analytics
        messageAnalyticsRepository.trackBatch(
            batchId = batchId,
            totalMessages = recipients.size,
            messageTemplate = messageTemplate,
            isPersonalized = true,
            totalCreditsUsed = totalCreditsRequired
        )

        Log.d("SmsRepository", "Sending personalized SMS request: $request")
        val response = arkeselApi.sendTemplateSms(request)
        Log.d("SmsRepository", "Personalized SMS response code: ${response.code()}")
        Log.d("SmsRepository", "Personalized SMS response body: ${response.body()}")
        Log.d("SmsRepository", "Personalized SMS error body: ${response.errorBody()?.string()}")

        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            if (responseBody.status == "success") {
                // Create a map of recipient to message ID for quick lookup
                val recipientToMessageId = responseBody.data.associate {
                    it.recipient to it.id
                }

                val results = recipients.map { (phoneNumber, _) ->
                    val messageId = recipientToMessageId[phoneNumber] ?: UUID.randomUUID().toString()
                    val isDelivered = recipientToMessageId.containsKey(phoneNumber)
                    val messageLength = recipientMessageLengths[phoneNumber] ?: 0
                    val messageSegments = recipientMessageSegments[phoneNumber] ?: 0
                    
                    // Track message in analytics
                    messageAnalyticsRepository.trackMessage(
                        messageId = messageId,
                        batchId = batchId,
                        recipient = phoneNumber,
                        sender = sender ?: "SMSBULKER",
                        messageLength = messageLength,
                        messageSegments = messageSegments,
                        creditsUsed = if (isDelivered) messageSegments else 0
                    )
                    
                    // Update message status
                    if (isDelivered) {
                        messageAnalyticsRepository.updateMessageStatus(
                            messageId = messageId,
                            status = SmsStatus.DELIVERED
                        )
                    } else {
                        messageAnalyticsRepository.updateMessageStatus(
                            messageId = messageId,
                            status = SmsStatus.FAILED,
                            errorCode = "RECIPIENT_NOT_FOUND",
                            errorMessage = "Recipient not found in API response"
                        )
                    }
                    
                    BulkSmsResult(
                        messageId = messageId,
                        recipient = phoneNumber,
                        status = if (isDelivered) "DELIVERED" else "FAILED",
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                emit(results)
            } else {
                val results = recipients.map { (phoneNumber, _) ->
                    val messageId = UUID.randomUUID().toString()
                    val messageLength = recipientMessageLengths[phoneNumber] ?: 0
                    val messageSegments = recipientMessageSegments[phoneNumber] ?: 0
                    
                    // Track failed message in analytics
                    messageAnalyticsRepository.trackMessage(
                        messageId = messageId,
                        batchId = batchId,
                        recipient = phoneNumber,
                        sender = sender ?: "SMSBULKER",
                        messageLength = messageLength,
                        messageSegments = messageSegments,
                        creditsUsed = 0 // No credits used for failed messages
                    )
                    
                    // Update message status to failed
                    messageAnalyticsRepository.updateMessageStatus(
                        messageId = messageId,
                        status = SmsStatus.FAILED,
                        errorCode = "API_ERROR",
                        errorMessage = "API returned failure status: ${responseBody.status}"
                    )
                    
                    BulkSmsResult(
                        messageId = messageId,
                        recipient = phoneNumber,
                        status = "FAILED",
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                emit(results)
            }
        } else {
            val results = recipients.map { (phoneNumber, _) ->
                val messageId = UUID.randomUUID().toString()
                val messageLength = recipientMessageLengths[phoneNumber] ?: 0
                val messageSegments = recipientMessageSegments[phoneNumber] ?: 0
                
                // Track failed message in analytics
                messageAnalyticsRepository.trackMessage(
                    messageId = messageId,
                    batchId = batchId,
                    recipient = phoneNumber,
                    sender = sender ?: "SMSBULKER",
                    messageLength = messageLength,
                    messageSegments = messageSegments,
                    creditsUsed = 0 // No credits used for failed messages
                )
                
                // Update message status to failed
                messageAnalyticsRepository.updateMessageStatus(
                    messageId = messageId,
                    status = SmsStatus.FAILED,
                    errorCode = "HTTP_ERROR",
                    errorMessage = "HTTP Error ${response.code()}: ${response.message()}"
                )
                
                BulkSmsResult(
                    messageId = messageId,
                    recipient = phoneNumber,
                    status = "FAILED",
                    timestamp = System.currentTimeMillis()
                )
            }
            
            emit(results)
        }
        
        // Update batch status
        messageAnalyticsRepository.updateBatchStatus(batchId)
    }
}