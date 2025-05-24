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
    private val userRepository: UserRepository // Added UserRepository parameter
) : BulkSmsRepository {

    private fun convertToArkeselTemplate(template: String): String {
        val regex = Regex("\\{(\\w+)\\}")
        return template.replace(regex) { matchResult ->
            val variableName = matchResult.groupValues[1]
            "<%$variableName%>"
        }
    }

    override suspend fun sendBulkSms(request: BulkSmsRequest): Result<BulkSmsResponse> {
        return try {
            // First, try to deduct credits
            val messageCount = request.recipients.size
            val deductResult = firebaseRepository.deductCredits(messageCount)
            if (deductResult.isFailure) {
                return Result.failure(deductResult.exceptionOrNull() ?: Exception("Failed to deduct credits"))
            }

            // If credit deduction successful, proceed with sending
            val messageText = request.messageTemplate?.let { template ->
                request.message?.let { convertToArkeselTemplate(it) } ?: convertToArkeselTemplate(template.content)
            } ?: request.message?.let { convertToArkeselTemplate(it) } ?: return Result.failure(Exception("No message content provided"))

            // Create recipients map
            val recipientsMap = if (request.messageTemplate?.variables?.isNotEmpty() == true) {
                // For personalized messages, include variables
                request.recipients.associate { recipient ->
                    recipient.phoneNumber to mapOf(
                        "number" to recipient.phoneNumber,
                        "name" to (recipient.name ?: recipient.phoneNumber)
                    ).plus(recipient.variables ?: emptyMap())
                }
            } else {
                // For regular messages, just include basic info
                request.recipients.associate { recipient ->
                    recipient.phoneNumber to mapOf(
                        "number" to recipient.phoneNumber,
                        "name" to (recipient.name ?: recipient.phoneNumber)
                    )
                }
            }

            // Get company name from user repository as fallback
            val fallbackSenderId = try {
                val user = userRepository.getCurrentUser()
                user.company?.takeIf { !it.isBlank() } ?: user.companyAlias.takeIf { !it.isBlank() } ?: "SMSBulker"
            } catch (e: Exception) {
                Log.w("BulkSmsRepository", "Failed to get company name: ${e.message}")
                "SMSBulker" // Generic fallback if everything fails
            }
            
            // Create the request
            val smsRequest = ArkeselSmsRequest(
                sender = request.senderId.takeIf { !it.isNullOrBlank() } ?: fallbackSenderId,
                message = if (request.messageTemplate?.variables?.isNotEmpty() == true) {
                    convertToArkeselTemplate(messageText)
                } else {
                    messageText
                },
                recipients = recipientsMap,
                sandbox = sandboxMode
            )

            Log.d("BulkSmsRepository", "Sending request: $smsRequest")
            
            // Send using appropriate endpoint
            val response = if (request.messageTemplate?.variables?.isNotEmpty() == true) {
                arkeselApi.sendTemplateSms(smsRequest)
            } else {
                arkeselApi.sendSms(smsRequest)
            }

            Log.d("BulkSmsRepository", "Response code: ${response.code()}")
            Log.d("BulkSmsRepository", "Response body: ${response.body()}")
            Log.d("BulkSmsRepository", "Error body: ${response.errorBody()?.string()}")

            if (response.isSuccessful && response.body() != null) {
                val responseData = response.body()!!
                if (responseData.status == "success" && responseData.data.isNotEmpty()) {
                    // Create message status entries for each recipient
                    val messageStatuses = responseData.data.map { result ->
                        MessageStatus(
                            messageId = result.id,
                            recipient = result.recipient,
                            status = BatchStatus.QUEUED,
                            timestamp = System.currentTimeMillis(),
                            errorMessage = null
                        )
                    }
                    
                    // Create the batch response
                    Result.success(BulkSmsResponse(
                        batchId = responseData.data.firstOrNull()?.id ?: UUID.randomUUID().toString(),
                        status = BatchStatus.QUEUED,
                        totalMessages = request.recipients.size,
                        messageStatuses = messageStatuses,
                        errorMessage = null,
                        timestamp = System.currentTimeMillis()
                    ))
                } else {
                    Result.failure(Exception("Failed to send messages: ${responseData.status}"))
                }
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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