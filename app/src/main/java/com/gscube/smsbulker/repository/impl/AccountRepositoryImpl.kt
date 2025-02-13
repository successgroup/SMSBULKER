package com.gscube.smsbulker.repository.impl

import android.content.Context
import com.gscube.smsbulker.data.CreditBalance
import com.gscube.smsbulker.data.Subscription
import com.gscube.smsbulker.data.UserProfile
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.data.network.AccountApiService
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.AccountRepository
import com.gscube.smsbulker.utils.SecureStorage
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import com.gscube.smsbulker.data.model.ChangePasswordRequest
import com.gscube.smsbulker.data.model.PasswordResetRequest
import com.gscube.smsbulker.data.model.EmailVerificationRequest

@Singleton
@ContributesBinding(AppScope::class)
class AccountRepositoryImpl @Inject constructor(
    private val apiService: AccountApiService,
    @Named("applicationContext") private val context: Context,
    private val secureStorage: SecureStorage
) : AccountRepository {
    
    override suspend fun getUserProfile(): Result<UserProfile> {
        return try {
            val response = apiService.getUserProfile()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch user profile"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getCreditBalance(): Result<CreditBalance> {
        return try {
            val response = apiService.getCreditBalance()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch credit balance"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getSubscription(): Result<Subscription> {
        return try {
            val response = apiService.getSubscription()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch subscription"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateProfile(profile: UserProfile): Result<UserProfile> {
        return try {
            val response = apiService.updateProfile(profile)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update profile"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> {
        return try {
            val response = apiService.changePassword(
                ChangePasswordRequest(
                    currentPassword = currentPassword,
                    newPassword = newPassword
                )
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to change password"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        return try {
            val response = apiService.requestPasswordReset(
                PasswordResetRequest(
                    email = email
                )
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to request password reset"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun verifyEmail(code: String): Result<Unit> {
        return try {
            val response = apiService.verifyEmail(
                EmailVerificationRequest(
                    code = code
                )
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to verify email"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun resendVerificationEmail(): Result<Unit> {
        return try {
            val response = apiService.resendVerificationEmail()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to resend verification email"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun refreshApiKey(): Result<Unit> {
        return try {
            val response = apiService.refreshApiKey()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to refresh API key: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
