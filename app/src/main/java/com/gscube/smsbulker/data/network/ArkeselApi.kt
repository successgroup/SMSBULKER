package com.gscube.smsbulker.data.network

import com.gscube.smsbulker.data.model.ArkeselSmsRequest
import com.gscube.smsbulker.data.model.ArkeselSmsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ArkeselApi {
    @POST("sms/send")
    suspend fun sendSms(@Body request: ArkeselSmsRequest): Response<ArkeselSmsResponse>
}
