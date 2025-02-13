package com.gscube.smsbulker.data.network

import com.gscube.smsbulker.data.*
import com.gscube.smsbulker.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface AccountApiService {
    @GET("account/profile")
    suspend fun getUserProfile(): Response<UserProfile>
    
    @GET("account/credits")
    suspend fun getCreditBalance(): Response<CreditBalance>
    
    @GET("account/subscription")
    suspend fun getSubscription(): Response<Subscription>
    
    @GET("account/limits")
    suspend fun getUsageLimits(): Response<UsageLimit>
    
    @GET("account/notifications/settings")
    suspend fun getNotificationSettings(): Response<NotificationSetting>
    
    @PUT("account/profile")
    suspend fun updateProfile(@Body profile: UserProfile): Response<UserProfile>
    
    @PUT("account/notifications/settings")
    suspend fun updateNotificationSettings(@Body settings: NotificationSetting): Response<NotificationSetting>
    
    @POST("account/password/change")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Unit>

    @POST("account/password/reset")
    suspend fun requestPasswordReset(@Body request: PasswordResetRequest): Response<Unit>

    @POST("account/email/verify")
    suspend fun verifyEmail(@Body request: EmailVerificationRequest): Response<Unit>

    @POST("account/email/resend-verification")
    suspend fun resendVerificationEmail(): Response<Unit>

    @POST("account/api-key/regenerate")
    suspend fun regenerateApiKey(): Response<String>
    
    @POST("account/subscription/change")
    suspend fun changePlan(@Body planId: String): Response<Subscription>
    
    @POST("account/subscription/cancel")
    suspend fun cancelSubscription(): Response<Subscription>
    
    @POST("account/subscription/renew")
    suspend fun renewSubscription(): Response<Subscription>
    
    @POST("account/apikey/refresh")
    suspend fun refreshApiKey(): Response<Unit>
} 