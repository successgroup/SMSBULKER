package com.gscube.smsbulker.data.network

import com.gscube.smsbulker.data.model.PaymentRequest
import com.gscube.smsbulker.data.model.PaymentResponse
import com.gscube.smsbulker.data.model.PaymentTransaction
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface PaystackApiService {
    /**
     * Initialize a Paystack transaction via Firebase Cloud Function
     * This will call our Firebase function that communicates with Paystack API
     */
    @POST("initializePaystackTransaction")
    suspend fun initializeTransaction(@Body request: PaymentRequest): Response<PaymentResponse>
    
    /**
     * Verify a Paystack transaction via Firebase Cloud Function
     * This will call our Firebase function that verifies the transaction with Paystack
     */
    @GET("verifyPaystackTransaction/{reference}")
    suspend fun verifyTransaction(@Path("reference") reference: String): Response<PaymentTransaction>
}