package com.gscube.smsbulker.data

data class UserProfile(
    val userId: String,
    val email: String,
    val name: String,
    val phone: String,
    val company: String? = null,
    val emailVerified: Boolean = false,
    val apiKey: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis()
)
