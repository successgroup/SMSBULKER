package com.gscube.smsbulker.repository

import com.gscube.smsbulker.data.LoginRequest
import com.gscube.smsbulker.data.SignupRequest

interface AuthRepository {
    suspend fun login(request: LoginRequest): Result<Unit>
    suspend fun signup(request: SignupRequest): Result<Unit>
    suspend fun signOut(): Result<Unit>
}