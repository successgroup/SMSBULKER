package com.gscube.smsbulker.data.api

import com.gscube.smsbulker.data.model.ArkeselSmsRequest
import com.gscube.smsbulker.data.model.ArkeselSmsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface SmsApiService {
    @POST("api/v2/sms/template/send")
    suspend fun sendTemplateMessage(
        @Header("api-key") apiKey: String,
        @Body request: ArkeselSmsRequest
    ): Response<ArkeselSmsResponse>
}
