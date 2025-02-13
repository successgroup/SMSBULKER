package com.gscube.smsbulker.repository.impl

import com.gscube.smsbulker.data.LoginRequest
import com.gscube.smsbulker.data.SignupRequest
import com.gscube.smsbulker.data.network.AuthApiService
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.AuthRepository
import com.squareup.anvil.annotations.ContributesBinding
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@ContributesBinding(AppScope::class)
class AuthRepositoryImpl @Inject constructor(
    private val authApiService: AuthApiService
) : AuthRepository {
    override suspend fun login(request: LoginRequest): Result<Unit> {
        return try {
            val response: Response<Unit> = authApiService.login(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Login failed: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signup(request: SignupRequest): Result<Unit> {
        return try {
            val response: Response<Unit> = authApiService.signup(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Signup failed: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            val response: Response<Unit> = authApiService.signOut()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Sign out failed: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
