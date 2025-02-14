package com.gscube.smsbulker.data.network

import com.gscube.smsbulker.data.model.PersonalizedSmsRequest
import com.gscube.smsbulker.data.model.Recipient
import com.gscube.smsbulker.data.model.SmsApiResponse
import retrofit2.Response
import retrofit2.http.*

interface SmsApiService {
    @POST("sms/send")
    suspend fun sendSms(
        @Query("api_key") apiKey: String,
        @Query("to") to: String,
        @Query("from") from: String,
        @Query("message") message: String
    ): Response<SmsApiResponse>

    @POST("sms/send/personalized")
    suspend fun sendPersonalizedSms(
        @Header("api-key") apiKey: String,
        @Body request: PersonalizedSmsRequest
    ): Response<SmsApiResponse>

    // Helper function to send bulk SMS by combining multiple recipients
    suspend fun sendBulkSms(
        apiKey: String,
        recipients: List<String>,
        message: String,
        sender: String
    ): Response<SmsApiResponse> {
        return sendSms(
            apiKey = apiKey,
            to = recipients.joinToString(","),
            from = sender,
            message = message
        )
    }

    // Helper function to send personalized bulk SMS
    suspend fun sendPersonalizedBulkSms(
        apiKey: String,
        message: String,
        sender: String,
        recipients: List<Recipient>,
        callbackUrl: String? = null
    ): Response<SmsApiResponse> {
        // Convert recipients list to the required map format
        val recipientsMap = recipients.associate { recipient ->
            recipient.phoneNumber to recipient.variables.mapValues { it.value.toString() }
        }

        val request = PersonalizedSmsRequest(
            sender = sender,
            message = message,
            recipients = recipientsMap,
            callback_url = callbackUrl
        )

        return sendPersonalizedSms(apiKey, request)
    }
}
