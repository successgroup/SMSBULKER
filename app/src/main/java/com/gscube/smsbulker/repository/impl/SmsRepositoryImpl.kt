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
                emit(BulkSmsResult(
                    messageId = result.id,
                    recipient = result.recipient,
                    status = "DELIVERED",
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

                emit(recipients.map { recipient ->
                    BulkSmsResult(
                        messageId = recipientToMessageId[recipient] ?: "",
                        recipient = recipient,
                        status = "DELIVERED",
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
            if (responseBody.status == "success") {
                // Create a map of recipient to message ID for quick lookup
                val recipientToMessageId = responseBody.data.associate {
                    it.recipient to it.id
                }

                emit(recipients.map { (phoneNumber, _) ->
                    BulkSmsResult(
                        messageId = recipientToMessageId[phoneNumber] ?: "",
                        recipient = phoneNumber,
                        status = "DELIVERED",
                        timestamp = System.currentTimeMillis()
                    )
                })
            } else {
                emit(recipients.map { (phoneNumber, _) ->
                    BulkSmsResult(
                        messageId = "",
                        recipient = phoneNumber,
                        status = "FAILED",
                        timestamp = System.currentTimeMillis()
                    )
                })
            }
        } else {
            emit(recipients.map { (phoneNumber, _) ->
                BulkSmsResult(
                    messageId = "",
                    recipient = phoneNumber,
                    status = "FAILED",
                    timestamp = System.currentTimeMillis()
                )
            })
        }
    }
}