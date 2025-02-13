package com.gscube.smsbulker.repository

import com.gscube.smsbulker.data.*

interface AccountRepository {
    suspend fun getUserProfile(): Result<UserProfile>
    suspend fun getCreditBalance(): Result<CreditBalance>
    suspend fun getSubscription(): Result<Subscription>
    suspend fun updateProfile(profile: UserProfile): Result<UserProfile>
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
    suspend fun requestPasswordReset(email: String): Result<Unit>
    suspend fun verifyEmail(code: String): Result<Unit>
    suspend fun resendVerificationEmail(): Result<Unit>
    suspend fun refreshApiKey(): Result<Unit>
}
