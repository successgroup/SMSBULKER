package com.gscube.smsbulker.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserProfile2(
    val userId: String,
    val name: String,
    val email: String,
    val phone: String,
    val company: String? = null,
    val apiKey: String,
    val createdAt: Long,
    val lastLogin: Long
) : Parcelable

@Parcelize
data class CreditBalance(
    val availableCredits: Double,
    val usedCredits: Double,
    val lastUpdated: Long,
    val nextRefillDate: Long? = null,
    val autoRefillEnabled: Boolean = false,
    val lowBalanceAlert: Double = 100.0
) : Parcelable

@Parcelize
data class Subscription(
    val planId: String,
    val planName: String,
    val status: String, // "active", "expired", "cancelled"
    val startDate: Long,
    val endDate: Long,
    val autoRenew: Boolean,
    val monthlyCredits: Double,
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