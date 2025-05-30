package com.gscube.smsbulker.data

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserProfile2(
    val userId: String,
    val name: String,
    val email: String,
    val phone: String,
    val company: String? = null,
    val apiKey: String,
    val createdAt: Timestamp,
    val lastLogin: Timestamp
) : Parcelable

@Parcelize
data class CreditBalance(
    val availableCredits: Int,
    val usedCredits: Int,
    val lastUpdated: Timestamp,
    val nextRefillDate: Timestamp? = null,
    val autoRefillEnabled: Boolean = false,
    val lowBalanceAlert: Int = 100
) : Parcelable

@Parcelize
data class Subscription(
    val planId: String,
    val planName: String,
    val status: String, // "active", "expired", "cancelled"
    val startDate: Timestamp,
    val endDate: Timestamp,
    val autoRenew: Boolean,
    val monthlyCredits: Int,
    val price: Double,
    val features: List<String>
) : Parcelable

@Parcelize
data class UsageLimit(
    val dailyMessageLimit: Int,
    val monthlyMessageLimit: Int,
    val concurrentRequests: Int,
    val rateLimitPerMinute: Int,
    val remainingDailyMessages: Int,
    val remainingMonthlyMessages: Int
) : Parcelable

@Parcelize
data class NotificationSetting(
    val lowBalanceAlert: Boolean = true,
    val lowBalanceThreshold: Double = 100.0,
    val deliveryReports: Boolean = true,
    val failureAlerts: Boolean = true,
    val newsletterSubscribed: Boolean = false,
    val emailNotifications: Boolean = true,
    val pushNotifications: Boolean = true
) : Parcelable