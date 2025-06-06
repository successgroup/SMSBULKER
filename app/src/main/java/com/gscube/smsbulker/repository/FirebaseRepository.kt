package com.gscube.smsbulker.repository

import com.gscube.smsbulker.data.CreditBalance
import com.gscube.smsbulker.data.Subscription
import com.gscube.smsbulker.data.UserProfile

interface FirebaseRepository {
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<UserProfile>
    suspend fun signUpWithEmailAndPassword(
        email: String,
        password: String,
        name: String,
        phone: String,
        company: String?
    ): Result<UserProfile>
    suspend fun getCurrentUser(): Result<UserProfile?>
    suspend fun signOut()
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun updateUserProfile(profile: UserProfile): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun verifyEmail(): Result<Unit>
    suspend fun isEmailVerified(): Boolean
    suspend fun reloadUser(): Result<Unit>
    // Add to FirebaseRepository.kt
    suspend fun refreshApiKey(): Result<Unit>
    suspend fun getCreditBalance(): Result<CreditBalance>
    suspend fun getSubscription(): Result<Subscription>
    suspend fun getCurrentUserCredits(): Result<Double>
    suspend fun deductCredits(messageCount: Int): Result<Double>
}