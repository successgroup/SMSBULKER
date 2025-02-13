package com.gscube.smsbulker.data.network

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
}
