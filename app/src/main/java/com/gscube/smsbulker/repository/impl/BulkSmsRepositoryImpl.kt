package com.gscube.smsbulker.repository.impl

import android.content.Context
import android.net.Uri
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.data.network.SmsApiService
import com.gscube.smsbulker.repository.BulkSmsRepository
import com.gscube.smsbulker.di.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@ContributesBinding(AppScope::class)
class BulkSmsRepositoryImpl @Inject constructor(
    @Named("applicationContext") private val context: Context,
    private val smsApiService: SmsApiService,
    @Named("api_key") private val apiKey: String
) : BulkSmsRepository {

    override suspend fun sendBulkSms(request: BulkSmsRequest): Result<BulkSmsResponse> {
        return try {
            val response = smsApiService.sendBulkSms(
                apiKey = apiKey,
                recipients = request.recipients.map { it.phoneNumber },
                message = request.message ?: request.messageTemplate?.content ?: "",
                sender = request.senderId
            )
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                Result.success(BulkSmsResponse(
                    batchId = data.messageId ?: "",
                    status = when (SmsStatus.fromString(data.status ?: "")) {
                        SmsStatus.SENT, SmsStatus.DELIVERED -> BatchStatus.COMPLETED
                        SmsStatus.PENDING -> BatchStatus.PROCESSING
                        SmsStatus.FAILED -> BatchStatus.FAILED
                    },
                    totalMessages = request.recipients.size,
                    cost = data.cost ?: 0.0,
                    units = data.credits ?: 0,
                    errorMessage = if (!response.isSuccessful) response.message() else null,
                    timestamp = System.currentTimeMillis()
                ))
            } else {
                Result.failure(Exception("API call failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBatchStatus(batchId: String): Result<BatchStatus> {
        return try {
            val response = smsApiService.sendBulkSms(
                apiKey = apiKey,
                recipients = listOf(batchId),
                message = "status",
                sender = "STATUS"
            )
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()!!.status ?: ""
                Result.success(when (SmsStatus.fromString(status)) {
                    SmsStatus.SENT, SmsStatus.DELIVERED -> BatchStatus.COMPLETED
                    SmsStatus.PENDING -> BatchStatus.PROCESSING
                    SmsStatus.FAILED -> BatchStatus.FAILED
                })
            } else {
                Result.failure(Exception("API call failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBatchMessageStatuses(batchId: String): Result<List<MessageStatus>> {
        return try {
            val response = smsApiService.sendBulkSms(
                apiKey = apiKey,
                recipients = listOf(batchId),
                message = "messages",
                sender = "STATUS"
            )
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                Result.success(listOf(MessageStatus(
                    messageId = data.messageId ?: "",
                    recipient = data.status ?: "",
                    status = when (SmsStatus.fromString(data.status ?: "")) {
                        SmsStatus.SENT, SmsStatus.DELIVERED -> BatchStatus.COMPLETED
                        SmsStatus.PENDING -> BatchStatus.PROCESSING
                        SmsStatus.FAILED -> BatchStatus.FAILED
                    },
                    timestamp = System.currentTimeMillis(),
                    errorMessage = if (!response.isSuccessful) response.message() else null
                )))
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

    override suspend fun getMessageStatus(messageId: String): Result<MessageStatus> {
        return try {
            val response = smsApiService.sendBulkSms(
                apiKey = apiKey,
                recipients = listOf(messageId),
                message = "status",
                sender = "STATUS"
            )
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                Result.success(MessageStatus(
                    messageId = messageId,
                    recipient = data.status ?: "",
                    status = when (SmsStatus.fromString(data.status ?: "")) {
                        SmsStatus.SENT, SmsStatus.DELIVERED -> BatchStatus.COMPLETED
                        SmsStatus.PENDING -> BatchStatus.PROCESSING
                        SmsStatus.FAILED -> BatchStatus.FAILED
                    },
                    timestamp = System.currentTimeMillis(),
                    errorMessage = if (!response.isSuccessful) response.message() else null
                ))
            } else {
                Result.failure(Exception("API call failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMessageStatus(messageId: String, status: MessageStatus): Result<Unit> {
        return try {
            val response = smsApiService.sendBulkSms(
                apiKey = apiKey,
                recipients = listOf(messageId),
                message = when (status.status) {
                    BatchStatus.COMPLETED -> "DELIVERED"
                    BatchStatus.FAILED -> "FAILED"
                    BatchStatus.PROCESSING -> "PENDING"
                    BatchStatus.QUEUED -> "QUEUED"
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
}