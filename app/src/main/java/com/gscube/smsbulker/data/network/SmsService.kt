package com.gscube.smsbulker.data.network

import retrofit2.Response
import retrofit2.http.*


interface SmsService {
    @POST(".")
    suspend fun sendSms(
        @Query("api_key") apiKey: String,
        @Body request: SmsApiRequest
    ): Response<SmsApiResponse>
}
