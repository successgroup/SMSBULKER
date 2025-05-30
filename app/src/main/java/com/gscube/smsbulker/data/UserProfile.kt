package com.gscube.smsbulker.data

import com.google.firebase.Timestamp

data class UserProfile(
    val userId: String,
    val email: String,
    val name: String,
    val phone: String,
    val company: String? = null,
    val companyAlias: String = "", // Company alias used as Sender ID
    val emailVerified: Boolean = false,
    val apiKey: String = "",
    val createdAt: Timestamp = Timestamp.now(), // Change to Timestamp
    val lastLogin: Timestamp = Timestamp.now(), // Change to Timestamp
    val creditBalance: CreditBalance? = null,
    val subscription: Subscription? = null
)
