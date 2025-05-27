package com.gscube.smsbulker.data.network

import com.gscube.smsbulker.data.model.ArkeselSmsRequest
import com.gscube.smsbulker.data.model.ArkeselSmsResponse
import com.gscube.smsbulker.data.model.DeliveryReport
import com.gscube.smsbulker.data.model.ArkeselAnalyticsRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ArkeselApi {
    @POST("/api/v2/sms/send")
    suspend fun sendSms(@Body request: ArkeselSmsRequest): Response<ArkeselSmsResponse>

    @POST("/api/v2/sms/template/send")
    suspend fun sendTemplateSms(@Body request: ArkeselSmsRequest): Response<ArkeselSmsResponse>
    
    // Add webhook endpoint for receiving delivery reports
    @POST("/webhook/delivery-report")
    suspend fun receiveDeliveryReport(@Body report: DeliveryReport): Response<Unit>
}
