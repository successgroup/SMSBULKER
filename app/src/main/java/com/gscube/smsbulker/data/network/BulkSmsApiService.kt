package com.gscube.smsbulker.data.network

import com.gscube.smsbulker.data.model.BulkSmsRequest
import com.gscube.smsbulker.data.model.BulkSmsResponse
import com.gscube.smsbulker.data.model.MessageStatus
import retrofit2.Response
import retrofit2.http.*

interface BulkSmsApiService {
    @POST("sms/bulk")
    suspend fun sendBulkSms(
        @Header("X-API-KEY") apiKey: String,
        @Body request: BulkSmsRequest
    ): Response<BulkSmsResponse>

    @GET("sms/batch/{batchId}")
    suspend fun getBatchStatus(
        @Header("X-API-KEY") apiKey: String,
        @Path("batchId") batchId: String
    ): Response<BulkSmsResponse>

    @GET("sms/batch/{batchId}/messages")
    suspend fun getBatchMessageStatuses(
        @Header("X-API-KEY") apiKey: String,
        @Path("batchId") batchId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): Response<List<MessageStatus>>

    @GET("sms/message/{messageId}")
    suspend fun getMessageStatus(
        @Header("X-API-KEY") apiKey: String,
        @Path("messageId") messageId: String
    ): Response<MessageStatus>
}