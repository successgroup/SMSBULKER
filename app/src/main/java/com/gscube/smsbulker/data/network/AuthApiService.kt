package com.gscube.smsbulker.data.network

import com.gscube.smsbulker.data.AuthResponse
import com.gscube.smsbulker.data.LoginRequest
import com.gscube.smsbulker.data.SignupRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<Unit>

    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<Unit>

    @POST("auth/signout")
    suspend fun signOut(): Response<Unit>
} 