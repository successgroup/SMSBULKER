package com.gscube.smsbulker.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AnalyticsSummary(
    val totalMessages: Int = 0,
    val deliveredMessages: Int = 0,
    val failedMessages: Int = 0,
    val creditsUsed: Int = 0,
    val creditsRemaining: Int = 0,
    val period: String = "" // "daily", "weekly", "monthly"
) : Parcelable {
    val successRate: Double
        get() = if (totalMessages > 0) deliveredMessages.toDouble() / totalMessages else 0.0

    val failureRate: Double
        get() = if (totalMessages > 0) failedMessages.toDouble() / totalMessages else 0.0

    val creditEfficiency: Double
        get() = if (creditsUsed > 0) totalMessages.toDouble() / creditsUsed else 0.0
}

@Parcelize
data class DailyStats(
    val timestamp: Long = 0,
    val totalMessages: Int = 0,
    val deliveredMessages: Int = 0,
    val failedMessages: Int = 0,
    val creditsUsed: Int = 0
) : Parcelable {
    val successRate: Double
        get() = if (totalMessages > 0) deliveredMessages.toDouble() / totalMessages else 0.0

    val failureRate: Double
        get() = if (totalMessages > 0) failedMessages.toDouble() / totalMessages else 0.0

    val creditEfficiency: Double
        get() = if (creditsUsed > 0) totalMessages.toDouble() / creditsUsed else 0.0
}

@Parcelize
data class FailureAnalysis(
    val reason: String = "",
    val count: Int = 0,
    val percentage: Double = 0.0
) : Parcelable

@Parcelize
data class CreditHistory(
    val timestamp: Long = 0,
    val amount: Int = 0,
    val type: String = "", // "purchase", "usage", "refund"
    val description: String = ""
) : Parcelable