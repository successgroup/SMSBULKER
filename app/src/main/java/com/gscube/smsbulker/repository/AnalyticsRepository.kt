package com.gscube.smsbulker.repository

import com.gscube.smsbulker.data.AnalyticsSummary
import com.gscube.smsbulker.data.DailyStats
import com.gscube.smsbulker.data.FailureAnalysis
import java.io.File

interface AnalyticsRepository {
    suspend fun getAnalyticsSummary(): AnalyticsSummary
    suspend fun getDailyStats(startTime: Long, endTime: Long): List<DailyStats>
    suspend fun getFailureAnalysis(): List<FailureAnalysis>
    suspend fun exportAnalytics(startTime: Long, endTime: Long): String
    suspend fun updateAnalytics(messagesSent: Int, messagesFailed: Int)
    suspend fun clearAnalytics()
    suspend fun exportAnalyticsData(
        summary: AnalyticsSummary?,
        stats: List<DailyStats>?,
        failures: List<FailureAnalysis>?,
        timestamp: String
    ): String
    suspend fun getMessageVolume(): Int
    suspend fun getDeliveryRate(): Float
    suspend fun getFailureRate(): Float
    suspend fun getAverageResponseTime(): Long
    suspend fun getCreditUsage(): Int
    suspend fun getCreditBalance(): Int
}