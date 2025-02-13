package com.gscube.smsbulker.repository.impl

import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.data.network.SmsApiService
import com.gscube.smsbulker.repository.SmsRepository
import com.gscube.smsbulker.di.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@ContributesBinding(AppScope::class)
class SmsRepositoryImpl @Inject constructor(
    private val smsApiService: SmsApiService,
    @Named("api_key") private val apiKey: String
) : SmsRepository {

    override suspend fun sendSingleSms(
        recipient: String,
        message: String,
        sender: String?
    ): Flow<BulkSmsResult> = flow {
        val response = smsApiService.sendBulkSms(
            apiKey = apiKey,
            recipients = listOf(recipient),
            message = message,
            sender = sender ?: "SMSBULKER"
        )

        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            emit(BulkSmsResult(
                messageId = responseBody.messageId,
                recipient = recipient,
                status = responseBody.status ?: "FAILED",
                timestamp = System.currentTimeMillis()
            ))
        } else {
            emit(BulkSmsResult(
                messageId = "",
                recipient = recipient,
                status = "FAILED",
                timestamp = System.currentTimeMillis()
            ))
        }
    }

    override suspend fun sendBulkSms(
        recipients: List<String>,
        message: String,
        sender: String?
    ): Flow<List<BulkSmsResult>> = flow {
        val response = smsApiService.sendBulkSms(
            apiKey = apiKey,
            recipients = recipients,
            message = message,
            sender = sender ?: "SMSBULKER"
        )

        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            emit(recipients.map { recipient ->
                BulkSmsResult(
                    messageId = responseBody.messageId,
                    recipient = recipient,
                    status = responseBody.status ?: "FAILED",
                    timestamp = System.currentTimeMillis()
                )
            })
        } else {
            emit(recipients.map { recipient ->
                BulkSmsResult(
                    messageId = "",
                    recipient = recipient,
                    status = "FAILED",
                    timestamp = System.currentTimeMillis()
                )
            })
        }
    }

    override suspend fun sendPersonalizedSms(
        recipients: List<Pair<String, Map<String, String>>>,
        messageTemplate: String,
        sender: String?
    ): Flow<List<BulkSmsResult>> = flow {
        val results = recipients.map { (recipient, variables) ->
            var personalizedMessage = messageTemplate
            variables.forEach { (key, value) ->
                personalizedMessage = personalizedMessage.replace("{$key}", value)
            }
            
            val response = smsApiService.sendBulkSms(
                apiKey = apiKey,
                recipients = listOf(recipient),
                message = personalizedMessage,
                sender = sender ?: "SMSBULKER"
            )

            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body()!!
                BulkSmsResult(
                    messageId = responseBody.messageId,
                    recipient = recipient,
                    status = responseBody.status ?: "FAILED",
                    timestamp = System.currentTimeMillis()
                )
            } else {
                BulkSmsResult(
                    messageId = "",
                    recipient = recipient,
                    status = "FAILED",
                    timestamp = System.currentTimeMillis()
                )
            }
        }
        emit(results)
    }
}