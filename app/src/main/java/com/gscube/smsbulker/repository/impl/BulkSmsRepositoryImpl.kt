package com.gscube.smsbulker.repository.impl

import android.content.Context
import android.net.Uri
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.data.network.SmsApiService
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.BulkSmsRepository
import com.gscube.smsbulker.utils.SecureStorage
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@ContributesBinding(AppScope::class)
class BulkSmsRepositoryImpl @Inject constructor(
    @Named("applicationContext") private val context: Context,
    private val smsApiService: SmsApiService,
    private val secureStorage: SecureStorage
) : BulkSmsRepository {

    override suspend fun sendBulkSms(request: BulkSmsRequest): Result<BulkSmsResponse> {
        return try {
            val apiKey = secureStorage.getApiKey()
                ?: return Result.failure(Exception("API key not found"))

            // Get the final message text
            val messageText = request.messageTemplate?.let { template ->
                request.message ?: template.content
            } ?: request.message ?: return Result.failure(Exception("No message content provided"))

            // Send using personalized API if the message has variables
            val response = if (request.messageTemplate?.variables?.isNotEmpty() == true) {
                smsApiService.sendPersonalizedBulkSms(
                    apiKey = apiKey,
                    message = messageText,
                    sender = request.senderId,
                    recipients = request.recipients
                )
            } else {
                // Use regular bulk SMS for non-personalized messages
                smsApiService.sendBulkSms(
                    apiKey = apiKey,
                    recipients = request.recipients.map { it.phoneNumber },
                    message = messageText,
                    sender = request.senderId
                )
            }

            if (response.isSuccessful) {
                val data = response.body() ?: throw Exception("No response data")
                Result.success(BulkSmsResponse(
                    batchId = data.messageId ?: UUID.randomUUID().toString(),
                    status = if (data.success) BatchStatus.QUEUED else BatchStatus.FAILED,
                    totalMessages = request.recipients.size,
                    cost = data.cost ?: 0.0,
                    units = data.credits ?: 0,
                    errorMessage = if (!response.isSuccessful) response.message() else null,
                    timestamp = System.currentTimeMillis()
                ))
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBatchStatus(batchId: String): Result<BatchStatus> {
        return try {
            val apiKey = secureStorage.getApiKey()
                ?: return Result.failure(Exception("API key not found"))

            val response = smsApiService.sendBulkSms(
                apiKey = apiKey,
                recipients = listOf(batchId),
                message = "status",
                sender = "STATUS"
            )

            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                Result.success(
                    when (SmsStatus.fromString(data.status ?: "")) {
                        SmsStatus.SENT, SmsStatus.DELIVERED -> BatchStatus.COMPLETED
                        SmsStatus.PENDING -> BatchStatus.PROCESSING
                        SmsStatus.FAILED -> BatchStatus.FAILED
                    }
                )
            } else {
                Result.failure(Exception("API call failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBatchMessageStatuses(batchId: String): Result<List<MessageStatus>> {
        return try {
            val apiKey = secureStorage.getApiKey()
                ?: return Result.failure(Exception("API key not found"))

            val response = smsApiService.sendBulkSms(
                apiKey = apiKey,
                recipients = listOf(batchId),
                message = "messages",
                sender = "STATUS"
            )

            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                // Parse response and convert to MessageStatus list
                Result.success(listOf(
                    MessageStatus(
                        messageId = data.messageId ?: "",
                        recipient = "",
                        status = when (SmsStatus.fromString(data.status ?: "")) {
                            SmsStatus.SENT, SmsStatus.DELIVERED -> BatchStatus.COMPLETED
                            SmsStatus.PENDING -> BatchStatus.PROCESSING
                            SmsStatus.FAILED -> BatchStatus.FAILED
                        },
                        timestamp = System.currentTimeMillis(),
                        errorMessage = if (!data.success) data.message else null
                    )
                ))
            } else {
                Result.failure(Exception("API call failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMessageStatus(messageId: String): Result<MessageStatus> {
        return try {
            val apiKey = secureStorage.getApiKey()
                ?: return Result.failure(Exception("API key not found"))

            val response = smsApiService.sendBulkSms(
                apiKey = apiKey,
                recipients = listOf(messageId),
                message = "status",
                sender = "STATUS"
            )

            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                Result.success(
                    MessageStatus(
                        messageId = messageId,
                        recipient = "",
                        status = when (SmsStatus.fromString(data.status ?: "")) {
                            SmsStatus.SENT, SmsStatus.DELIVERED -> BatchStatus.COMPLETED
                            SmsStatus.PENDING -> BatchStatus.PROCESSING
                            SmsStatus.FAILED -> BatchStatus.FAILED
                        },
                        timestamp = System.currentTimeMillis(),
                        errorMessage = if (!data.success) data.message else null
                    )
                )
            } else {
                Result.failure(Exception("API call failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMessageStatus(messageId: String, status: MessageStatus): Result<Unit> {
        return try {
            val apiKey = secureStorage.getApiKey()
                ?: return Result.failure(Exception("API key not found"))

            val response = smsApiService.sendBulkSms(
                apiKey = apiKey,
                recipients = listOf(messageId),
                message = when (status.status) {
                    BatchStatus.COMPLETED -> "DELIVERED"
                    BatchStatus.FAILED -> "FAILED"
                    else -> "PENDING"
                },
                sender = "STATUS"
            )

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("API call failed: ${response.message()}"))
            }
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