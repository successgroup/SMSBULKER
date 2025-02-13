package com.gscube.smsbulker.data.network

import com.gscube.smsbulker.data.model.SmsRequest
import com.gscube.smsbulker.data.model.SmsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ArkeselApi {
    @POST("sms/send")
    suspend fun sendSms(@Body request: SmsRequest): Response<SmsResponse>
}
