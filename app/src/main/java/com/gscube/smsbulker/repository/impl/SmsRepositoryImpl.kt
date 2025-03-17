package com.gscube.smsbulker.repository.impl

import android.util.Log
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.data.network.ArkeselApi
import com.gscube.smsbulker.repository.SmsRepository
import com.gscube.smsbulker.di.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Singleton
@ContributesBinding(AppScope::class)
class SmsRepositoryImpl @Inject constructor(
    private val arkeselApi: ArkeselApi,
    @Named("sandbox_mode") private val sandboxMode: Boolean = false
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
            sandbox = sandboxMode
        )

        Log.d("SmsRepository", "Sending SMS request: $request")
        val response = arkeselApi.sendSms(request)
        Log.d("SmsRepository", "SMS response code: ${response.code()}")
        Log.d("SmsRepository", "SMS response body: ${response.body()}")
        Log.d("SmsRepository", "SMS error body: ${response.errorBody()?.string()}")

        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            emit(BulkSmsResult(
                messageId = responseBody.data?.messageId ?: "",
                recipient = recipient,
                status = if (responseBody.code == 200) "DELIVERED" else "FAILED",
                timestamp = System.currentTimeMillis(),
                cost = responseBody.data?.cost ?: 0.0
            ))
        } else {
            emit(BulkSmsResult(
                messageId = "",
                recipient = recipient,
                status = "FAILED",
                timestamp = System.currentTimeMillis(),
                cost = 0.0
            ))
        }
    }

    override suspend fun sendBulkSms(
        recipients: List<String>,
        message: String,
        sender: String?
    ): Flow<List<BulkSmsResult>> = flow {
        val recipientsMap = recipients.associateWith { recipient ->
            mapOf(
                "number" to recipient,
                "name" to recipient
            )
        }

        val request = ArkeselSmsRequest(
            sender = sender ?: "SMSBULKER",
            message = if (message.contains("{")) {
                convertToArkeselTemplate(message)
            } else {
                message
            },
            recipients = recipientsMap,
            sandbox = sandboxMode
        )

        Log.d("SmsRepository", "Sending bulk SMS request: $request")
        val response = arkeselApi.sendSms(request)
        Log.d("SmsRepository", "Bulk SMS response code: ${response.code()}")
        Log.d("SmsRepository", "Bulk SMS response body: ${response.body()}")
        Log.d("SmsRepository", "Bulk SMS error body: ${response.errorBody()?.string()}")

        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            emit(recipients.map { recipient ->
                BulkSmsResult(
                    messageId = responseBody.data?.messageId ?: "",
                    recipient = recipient,
                    status = if (responseBody.code == 200) "DELIVERED" else "FAILED",
                    timestamp = System.currentTimeMillis(),
                    cost = responseBody.data?.cost?.div(recipients.size) ?: 0.0
                )
            })
        } else {
            emit(recipients.map { recipient ->
                BulkSmsResult(
                    messageId = "",
                    recipient = recipient,
                    status = "FAILED",
                    timestamp = System.currentTimeMillis(),
                    cost = 0.0
                )
            })
        }
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

        Log.d("SmsRepository", "Sending personalized SMS request: $request")
        val response = arkeselApi.sendTemplateSms(request)
        Log.d("SmsRepository", "Personalized SMS response code: ${response.code()}")
        Log.d("SmsRepository", "Personalized SMS response body: ${response.body()}")
        Log.d("SmsRepository", "Personalized SMS error body: ${response.errorBody()?.string()}")

        if (response.isSuccessful && response.body() != null) {
            val responseBody = response.body()!!
            emit(recipients.map { (phoneNumber, _) ->
                BulkSmsResult(
                    messageId = responseBody.data?.messageId ?: "",
                    recipient = phoneNumber,
                    status = if (responseBody.code == 200) "DELIVERED" else "FAILED",
                    timestamp = System.currentTimeMillis(),
                    cost = responseBody.data?.cost?.div(recipients.size) ?: 0.0
                )
            })
        } else {
            emit(recipients.map { (phoneNumber, _) ->
                BulkSmsResult(
                    messageId = "",
                    recipient = phoneNumber,
                    status = "FAILED",
                    timestamp = System.currentTimeMillis(),
                    cost = 0.0
                )
            })
        }
    }
}