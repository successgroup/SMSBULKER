package com.gscube.smsbulker.repository.impl

import android.content.Context
import com.gscube.smsbulker.data.AnalyticsSummary
import com.gscube.smsbulker.data.DailyStats
import com.gscube.smsbulker.data.FailureAnalysis
import com.gscube.smsbulker.data.network.AnalyticsApiService
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.AnalyticsRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@ContributesBinding(AppScope::class)
class AnalyticsRepositoryImpl @Inject constructor(
    private val apiService: AnalyticsApiService,
    @Named("applicationContext") private val context: Context,
    private val firestore: FirebaseFirestore
) : AnalyticsRepository {

    override suspend fun getAnalyticsSummary(): AnalyticsSummary {
        val summaryRef = firestore.collection("analytics").document("summary")
        val snapshot = summaryRef.get().await()
        return snapshot.toObject(AnalyticsSummary::class.java) ?: AnalyticsSummary(
            totalMessages = 0,
            deliveredMessages = 0,
            failedMessages = 0,
            creditsUsed = 0,
            creditsRemaining = 0
        )
    }

    override suspend fun getDailyStats(startTime: Long, endTime: Long): List<DailyStats> {
        return firestore.collection("analytics")
            .document("daily_stats")
            .collection("stats")
            .whereGreaterThanOrEqualTo("timestamp", startTime)
            .whereLessThanOrEqualTo("timestamp", endTime)
            .get()
            .await()
            .toObjects(DailyStats::class.java)
    }

    override suspend fun getFailureAnalysis(): List<FailureAnalysis> {
        return try {
            val failuresRef = firestore.collection("analytics").document("failures")
            val snapshot = failuresRef.get().await()
            if (snapshot.exists()) {
                listOf(snapshot.toObject(FailureAnalysis::class.java)!!)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun clearAnalytics() {
        coroutineScope {
            val batch = firestore.batch()
            val summaryRef = firestore.collection("analytics").document("summary")
            val failuresRef = firestore.collection("analytics").document("failures")
            val statsRef = firestore.collection("analytics").document("daily_stats")

            batch.delete(summaryRef)
            batch.delete(failuresRef)
            batch.delete(statsRef)

            batch.commit().await()
        }
    }

    override suspend fun exportAnalytics(startTime: Long, endTime: Long): String {
        val summary = getAnalyticsSummary()
        val dailyStats = getDailyStats(startTime, endTime)
        val failures = getFailureAnalysis()
        
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            .format(Date())
        
        return exportAnalyticsData(summary, dailyStats, failures, timestamp)
    }

    override suspend fun exportAnalyticsData(
        summary: AnalyticsSummary?,
        stats: List<DailyStats>?,
        failures: List<FailureAnalysis>?,
        timestamp: String
    ): String = coroutineScope {
        val writer = StringWriter()
        writer.use { w ->
            // Write Summary
            summary?.let {
                val deliveryRate = async { getDeliveryRate() }.await()
                val failureRate = async { getFailureRate() }.await()
                val avgResponseTime = async { getAverageResponseTime() }.await()
                
                w.write("Summary:\n")
                w.write("Total Messages,${it.totalMessages}\n")
                w.write("Delivered Messages,${it.deliveredMessages}\n")
                w.write("Failed Messages,${it.failedMessages}\n")
                w.write("Delivery Rate,${String.format("%.2f%%", deliveryRate * 100)}\n")
                w.write("Failure Rate,${String.format("%.2f%%", failureRate * 100)}\n")
                w.write("Average Response Time,${String.format("%.2f ms", avgResponseTime)}\n")
                w.write("Credits Used,${it.creditsUsed}\n")
                w.write("Credits Remaining,${it.creditsRemaining}\n\n")
            }

            // Write Daily Stats
            stats?.let {
                w.write("Daily Statistics:\n")
                w.write("Date,Total Messages,Delivered,Failed,Success Rate,Credits Used\n")
                for (stat in it) {
                    val successRate = if (stat.totalMessages > 0) {
                        stat.deliveredMessages.toFloat() / stat.totalMessages
                    } else 0f
                    w.write("${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(stat.timestamp))}," +
                        "${stat.totalMessages}," +
                        "${stat.deliveredMessages}," +
                        "${stat.failedMessages}," +
                        "${String.format("%.2f%%", successRate * 100)}," +
                        "${stat.creditsUsed}\n"
                    )
                }
                w.write("\n")
            }

            // Write Failure Analysis
            failures?.let {
                w.write("Failure Analysis:\n")
                w.write("Error Type,Count,Percentage\n")
                for (failure in it) {
                    w.write("${failure.reason}," +
                        "${failure.count}," +
                        "${String.format("%.2f%%", failure.percentage * 100)}\n"
                    )
                }
            }
        }
        writer.toString()
    }

    override suspend fun getMessageVolume(): Int {
        val summary = getAnalyticsSummary()
        return summary.totalMessages
    }

    override suspend fun getDeliveryRate(): Float {
        val summary = getAnalyticsSummary()
        return if (summary.totalMessages > 0) {
            summary.deliveredMessages.toFloat() / summary.totalMessages
        } else 0f
    }

    override suspend fun getFailureRate(): Float {
        val summary = getAnalyticsSummary()
        return if (summary.totalMessages > 0) {
            summary.failedMessages.toFloat() / summary.totalMessages
        } else 0f
    }

    override suspend fun getAverageResponseTime(): Long {
        return try {
            val response = apiService.getAverageResponseTime()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    override suspend fun getCreditUsage(): Int {
        val summary = getAnalyticsSummary()
        return summary.creditsUsed
    }

    override suspend fun getCreditBalance(): Int {
        val summary = getAnalyticsSummary()
        return summary.creditsRemaining
    }

    override suspend fun updateAnalytics(messagesSent: Int, messagesFailed: Int) {
        coroutineScope {
            // Update summary
            val summaryRef = firestore.collection("analytics").document("summary")
            val currentSummary = getAnalyticsSummary()
            
            val updatedSummary = currentSummary.copy(
                totalMessages = currentSummary.totalMessages + messagesSent,
                deliveredMessages = currentSummary.deliveredMessages + (messagesSent - messagesFailed),
                failedMessages = currentSummary.failedMessages + messagesFailed,
                creditsUsed = currentSummary.creditsUsed + messagesSent
            )
            
            summaryRef.set(updatedSummary).await()
            
            // Update daily stats
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val dailyStatsRef = firestore.collection("analytics")
                .document("daily_stats")
                .collection("stats")
                .document(today.toString())
            
            val dailyStats = dailyStatsRef.get().await()
            if (dailyStats.exists()) {
                val currentStats = dailyStats.toObject(DailyStats::class.java)!!
                val updatedStats = currentStats.copy(
                    totalMessages = currentStats.totalMessages + messagesSent,
                    deliveredMessages = currentStats.deliveredMessages + (messagesSent - messagesFailed),
                    failedMessages = currentStats.failedMessages + messagesFailed,
                    creditsUsed = currentStats.creditsUsed + messagesSent
                )
                dailyStatsRef.set(updatedStats).await()
            } else {
                val newStats = DailyStats(
                    timestamp = today,
                    totalMessages = messagesSent,
                    deliveredMessages = messagesSent - messagesFailed,
                    failedMessages = messagesFailed,
                    creditsUsed = messagesSent
                )
                dailyStatsRef.set(newStats).await()
            }
        }
    }
}
