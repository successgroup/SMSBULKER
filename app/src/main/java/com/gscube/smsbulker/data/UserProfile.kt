package com.gscube.smsbulker.data

data class UserProfile(
    val userId: String,
    val email: String,
    val name: String,
    val phone: String,
    val company: String? = null,
    val companyAlias: String = "", // Company alias used as Sender ID
    val emailVerified: Boolean = false,
    val apiKey: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
    val creditBalance: CreditBalance? = null,
    val subscription: Subscription? = null
)
