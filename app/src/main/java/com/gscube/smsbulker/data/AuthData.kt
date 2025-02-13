package com.gscube.smsbulker.data

data class LoginRequest(
    val email: String,
    val password: String
)

data class SignupRequest(
    val email: String,
    val password: String,
    val companyName: String,
    val phoneNumber: String
)

data class AuthResponse(
    val success: Boolean,
    val token: String?,
    val error: String?,
    val userId: String?
) 