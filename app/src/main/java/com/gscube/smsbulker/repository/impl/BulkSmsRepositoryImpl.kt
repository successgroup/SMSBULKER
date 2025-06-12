package com.gscube.smsbulker.repository.impl

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.data.network.ArkeselApi
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.BulkSmsRepository
import com.gscube.smsbulker.repository.FirebaseRepository
import com.gscube.smsbulker.repository.UserRepository
import com.gscube.smsbulker.utils.SecureStorage
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@ContributesBinding(AppScope::class)
class BulkSmsRepositoryImpl @Inject constructor(
    @Named("applicationContext") private val context: Context,
    private val arkeselApi: ArkeselApi,
    @Named("sandbox_mode") private val sandboxMode: Boolean,
    private val secureStorage: SecureStorage,
    private val firebaseRepository: FirebaseRepository,
    private val userRepository: UserRepository
) : BulkSmsRepository {

    companion object {
        private const val BATCH_SIZE = 100 // API limit
        private const val BATCH_DELAY_MS = 1000L // 1 second delay between batches to avoid rate limiting
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L // 2 seconds delay between retries
    }

    private fun convertToArkeselTemplate(template: String): String {
        val regex = Regex("\\{(\\w+)\\}")
        return template.replace(regex) { matchResult ->
            val variableName = matchResult.groupValues[1]
            "<%$variableName%>"
        }
    }

    override suspend fun sendBulkSms(request: BulkSmsRequest): Result<BulkSmsResponse> {
        return try {
            val messageCount = request.recipients.size
            
            // Log API key (masked for security)
            val apiKey = secureStorage.getApiKey()
            Log.d("BulkSmsRepository", "API Key available: ${!apiKey.isNullOrEmpty()}")
            if (apiKey.isNullOrEmpty()) {
                Log.e("BulkSmsRepository", "API Key is null or empty")
                return Result.failure(Exception("API Key not found. Please check your account settings."))
            }
            
            // First, check if user has enough credits without deducting
            val currentCredits = firebaseRepository.getCurrentUserCredits()
            Log.d("BulkSmsRepository", "Current credits: ${currentCredits.getOrNull() ?: "Unknown"}")
            Log.d("BulkSmsRepository", "Required credits: $messageCount")
            Log.d("BulkSmsRepository", "Credits check result: ${currentCredits.isSuccess}")
            
            if (currentCredits.isFailure || (currentCredits.getOrNull() ?: 0.0) < messageCount) {
                Log.e("BulkSmsRepository", "Credit check failed - Available: ${currentCredits.getOrNull()}, Required: $messageCount")
                return Result.failure(Exception("Insufficient credits"))
            }

            // Prepare message text
            val messageText = request.messageTemplate?.let { template ->
                request.message?.let { convertToArkeselTemplate(it) } ?: convertToArkeselTemplate(template.content)
            } ?: request.message?.let { convertToArkeselTemplate(it) } ?: return Result.failure(Exception("No message content provided"))

            // Get fallback sender ID
            val fallbackSenderId = try {
                val user = userRepository.getCurrentUser()
                user.company?.takeIf { !it.isBlank() } ?: user.companyAlias.takeIf { !it.isBlank() } ?: "SMSBulker"
            } catch (e: Exception) {
                Log.w("BulkSmsRepository", "Failed to get company name: ${e.message}")
                "SMSBulker"
            }

            // Split recipients into batches
            val recipientBatches = request.recipients.chunked(BATCH_SIZE)
            val totalBatches = recipientBatches.size
            val batchId = UUID.randomUUID().toString()
            
            Log.d("BulkSmsRepository", "Processing $messageCount recipients in $totalBatches batches of max $BATCH_SIZE")

            // Process batches with retry logic
            val allMessageStatuses = mutableListOf<MessageStatus>()
            var successfulBatches = 0
            var totalCreditsDeducted = 0

            for ((batchIndex, recipientBatch) in recipientBatches.withIndex()) {
                Log.d("BulkSmsRepository", "Processing batch ${batchIndex + 1}/$totalBatches with ${recipientBatch.size} recipients")
                
                val batchResult = processBatchWithRetry(
                    recipientBatch = recipientBatch,
                    messageText = messageText,
                    senderId = request.senderId.takeIf { !it.isNullOrBlank() } ?: fallbackSenderId,
                    request = request,
                    batchIndex = batchIndex + 1,
                    totalBatches = totalBatches
                )

                if (batchResult.isSuccess) {
                    val batchStatuses = batchResult.getOrNull() ?: emptyList()
                    allMessageStatuses.addAll(batchStatuses)
                    successfulBatches++
                    totalCreditsDeducted += recipientBatch.size
                    
                    // Add delay between batches to avoid rate limiting (except for last batch)
                    if (batchIndex < recipientBatches.size - 1) {
                        Log.d("BulkSmsRepository", "Waiting ${BATCH_DELAY_MS}ms before next batch...")
                        delay(BATCH_DELAY_MS)
                    }
                } else {
                    // Handle batch failure - add failed statuses for this batch
                    val failedStatuses = recipientBatch.map { recipient ->
                        MessageStatus(
                            messageId = "",
                            recipient = recipient.phoneNumber,
                            status = BatchStatus.FAILED,
                            timestamp = System.currentTimeMillis(),
                            errorMessage = batchResult.exceptionOrNull()?.message
                        )
                    }
                    allMessageStatuses.addAll(failedStatuses)
                    Log.e("BulkSmsRepository", "Batch ${batchIndex + 1} failed: ${batchResult.exceptionOrNull()?.message}")
                }
            }

            // Deduct credits only for successful messages
            if (totalCreditsDeducted > 0) {
                val deductResult = firebaseRepository.deductCredits(totalCreditsDeducted)
                if (deductResult.isFailure) {
                    Log.w("BulkSmsRepository", "SMS sent successfully but failed to deduct credits: ${deductResult.exceptionOrNull()?.message}")
                }
            }

            // Determine overall status
            val overallStatus = when {
                successfulBatches == totalBatches -> BatchStatus.COMPLETED
                successfulBatches > 0 -> BatchStatus.PARTIAL_SUCCESS
                else -> BatchStatus.FAILED
            }

            Log.d("BulkSmsRepository", "Bulk SMS completed: $successfulBatches/$totalBatches batches successful, $totalCreditsDeducted credits deducted")

            Result.success(BulkSmsResponse(
                batchId = batchId,
                status = overallStatus,
                totalMessages = messageCount,
                messageStatuses = allMessageStatuses,
                errorMessage = if (overallStatus == BatchStatus.FAILED) "All batches failed" else null,
                timestamp = System.currentTimeMillis()
            ))

        } catch (e: Exception) {
            Log.e("BulkSmsRepository", "Exception in sendBulkSms: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun processBatchWithRetry(
        recipientBatch: List<Recipient>,
        messageText: String,
        senderId: String,
        request: BulkSmsRequest,
        batchIndex: Int,
        totalBatches: Int
    ): Result<List<MessageStatus>> {
        var lastException: Exception? = null
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d("BulkSmsRepository", "Batch $batchIndex/$totalBatches - Attempt ${attempt + 1}/$MAX_RETRIES")
                
                val result = processSingleBatch(recipientBatch, messageText, senderId, request)
                if (result.isSuccess) {
                    return result
                }
                lastException = result.exceptionOrNull() as? Exception
                
            } catch (e: Exception) {
                lastException = e
                Log.w("BulkSmsRepository", "Batch $batchIndex attempt ${attempt + 1} failed: ${e.message}")
            }
            
            // Wait before retry (except for last attempt)
            if (attempt < MAX_RETRIES - 1) {
                Log.d("BulkSmsRepository", "Retrying batch $batchIndex in ${RETRY_DELAY_MS}ms...")
                delay(RETRY_DELAY_MS)
            }
        }
        
        return Result.failure(lastException ?: Exception("Batch processing failed after $MAX_RETRIES attempts"))
    }

    private suspend fun processSingleBatch(
        recipientBatch: List<Recipient>,
        messageText: String,
        senderId: String,
        request: BulkSmsRequest
    ): Result<List<MessageStatus>> {
        // Create recipients map for this batch
        val recipientsMap = if (request.messageTemplate?.variables?.isNotEmpty() == true) {
            // For personalized messages, include variables
            recipientBatch.associate { recipient ->
                recipient.phoneNumber to mapOf(
                    "number" to recipient.phoneNumber,
                    "name" to (recipient.name ?: recipient.phoneNumber)
                ).plus(recipient.variables ?: emptyMap())
            }
        } else {
            // For regular messages, just include basic info
            recipientBatch.associate { recipient ->
                recipient.phoneNumber to mapOf(
                    "number" to recipient.phoneNumber,
                    "name" to (recipient.name ?: recipient.phoneNumber)
                )
            }
        }

        // Create the API request for this batch
        val smsRequest = ArkeselSmsRequest(
            sender = senderId,
            message = if (request.messageTemplate?.variables?.isNotEmpty() == true) {
                convertToArkeselTemplate(messageText)
            } else {
                messageText
            },
            recipients = recipientsMap,
            sandbox = sandboxMode
        )

        Log.d("BulkSmsRepository", "Sending batch request with ${recipientBatch.size} recipients")
        val response = arkeselApi.sendTemplateSms(smsRequest)

        Log.d("BulkSmsRepository", "Batch response code: ${response.code()}")
        Log.d("BulkSmsRepository", "Batch response body: ${response.body()}")
        
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            Log.e("BulkSmsRepository", "Batch error body: $errorBody")
            return Result.failure(Exception("API Error ${response.code()}: ${response.message()}"))
        }

        val responseData = response.body()
        if (responseData == null) {
            return Result.failure(Exception("Empty response body"))
        }

        if (responseData.status != "success" || responseData.data.isEmpty()) {
            return Result.failure(Exception("API returned failure status: ${responseData.status}"))
        }

        // Create message status entries for this batch
        val messageStatuses = responseData.data.map { result ->
            MessageStatus(
                messageId = result.id,
                recipient = result.recipient,
                status = BatchStatus.QUEUED,
                timestamp = System.currentTimeMillis(),
                errorMessage = null
            )
        }

        return Result.success(messageStatuses)
    }

    override suspend fun getBatchStatus(batchId: String): Result<BatchStatus> {
        return try {
            // For now, just return COMPLETED since v2 API doesn't have status check
            Result.success(BatchStatus.COMPLETED)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBatchMessageStatuses(batchId: String): Result<List<MessageStatus>> {
        return try {
            // For now, return empty list since v2 API doesn't have status check
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMessageStatus(messageId: String): Result<MessageStatus> {
        return try {
            // For now, return completed status since v2 API doesn't have status check
            Result.success(
                MessageStatus(
                    messageId = messageId,
                    recipient = "",
                    status = BatchStatus.COMPLETED,
                    timestamp = System.currentTimeMillis(),
                    errorMessage = null
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMessageStatus(messageId: String, status: MessageStatus): Result<Unit> {
        return try {
            // For now, just return success since v2 API doesn't have status update
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun parseRecipientsFromCsv(uri: Uri): Flow<List<Recipient>> = flow {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            val recipients = mutableListOf<Recipient>()
            var lineNumber = 0
            var line: String? = null
            var headerLine: String? = null

            try {
                // Read header line
                headerLine = reader.readLine()?.trim()
                if (headerLine == null) {
                    throw Exception("CSV file is empty")
                }
                lineNumber++

                // Process data lines
                while (reader.readLine()?.also { line = it } != null) {
                    lineNumber++
                    line?.trim()?.split(",")?.let { columns ->
                        if (columns.size >= 2) {
                            recipients.add(Recipient(
                                phoneNumber = columns[1].trim(),
                                name = columns[0].trim()
                            ))
                        }
                    }
                }

                emit(recipients)
            } catch (e: Exception) {
                throw Exception("Error at line $lineNumber: ${e.message}")
            }
        } ?: throw Exception("Could not open file")
    }.flowOn(Dispatchers.IO)
}