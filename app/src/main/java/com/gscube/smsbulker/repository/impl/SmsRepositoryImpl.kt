package com.gscube.smsbulker.repository.impl

import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.data.network.ArkeselApi
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
    private val arkeselApi: ArkeselApi,
    @Named("api_key") private val apiKey: String,
    @Named("sandbox_mode") private val sandboxMode: Boolean = false
) : SmsRepository {

    override suspend fun sendSingleSms(
        recipient: String,
        message: String,
        sender: String?
    ): Flow<BulkSmsResult> = flow {
        val request = ArkeselSmsRequest(
            sender = sender ?: "SMSBULKER",
            message = message,
            recipients = mapOf(
                recipient to mapOf(
                    "phone_number" to recipient
                )
            ),
            sandbox = sandboxMode,
            apiKey = apiKey
        )

        val response = arkeselApi.sendSms(request)

        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            emit(BulkSmsResult(
                messageId = responseBody.data?.messageId ?: "",
                recipient = recipient,
                status = responseBody.status,
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
        val recipientsMap = recipients.associate { recipient ->
            recipient to mapOf(
                "phone_number" to recipient
            )
        }

        val request = ArkeselSmsRequest(
            sender = sender ?: "SMSBULKER",
            message = message,
            recipients = recipientsMap,
            sandbox = sandboxMode,
            apiKey = apiKey
        )

        val response = arkeselApi.sendSms(request)

        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            emit(recipients.map { recipient ->
                BulkSmsResult(
                    messageId = responseBody.data?.messageId ?: "",
                    recipient = recipient,
                    status = responseBody.status,
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
        val recipientsMap = recipients.associate { (recipient, variables) ->
            recipient to variables + ("phone_number" to recipient)
        }

        val request = ArkeselSmsRequest(
            sender = sender ?: "SMSBULKER",
            message = messageTemplate,
            recipients = recipientsMap,
            sandbox = sandboxMode,
            apiKey = apiKey
        )

        val response = arkeselApi.sendSms(request)

        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            emit(recipients.map { (recipient, _) ->
                BulkSmsResult(
                    messageId = responseBody.data?.messageId ?: "",
                    recipient = recipient,
                    status = responseBody.status,
                    timestamp = System.currentTimeMillis()
                )
            })
        } else {
            emit(recipients.map { (recipient, _) ->
                BulkSmsResult(
                    messageId = "",
                    recipient = recipient,
                    status = "FAILED",
                    timestamp = System.currentTimeMillis()
                )
            })
        }
    }
}