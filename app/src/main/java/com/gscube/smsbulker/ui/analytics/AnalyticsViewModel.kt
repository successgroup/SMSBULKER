package com.gscube.smsbulker.ui.analytics

import android.os.Environment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.AnalyticsSummary
import com.gscube.smsbulker.data.DailyStats
import com.gscube.smsbulker.data.FailureAnalysis
import com.gscube.smsbulker.repository.AnalyticsRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class AnalyticsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _analyticsSummary = MutableLiveData<AnalyticsSummary>()
    val analyticsSummary: LiveData<AnalyticsSummary> = _analyticsSummary

    private val _dailyStats = MutableLiveData<List<DailyStats>>()
    val dailyStats: LiveData<List<DailyStats>> = _dailyStats

    private val _failureAnalysis = MutableLiveData<List<FailureAnalysis>>()
    val failureAnalysis: LiveData<List<FailureAnalysis>> = _failureAnalysis

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _exportedFile = MutableLiveData<String?>()
    val exportedFile: LiveData<String?> = _exportedFile

    init {
        loadAnalytics()
    }

    fun loadAnalytics(period: String = "daily") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val endTime = System.currentTimeMillis()
                val startTime = when (period) {
                    "weekly" -> endTime - 7 * 24 * 60 * 60 * 1000
                    "monthly" -> endTime - 30L * 24 * 60 * 60 * 1000
                    else -> endTime - 24 * 60 * 60 * 1000 // daily
                }
                
                _analyticsSummary.value = analyticsRepository.getAnalyticsSummary()
                _dailyStats.value = analyticsRepository.getDailyStats(startTime, endTime)
                val failures = analyticsRepository.getFailureAnalysis()
                _failureAnalysis.value = failures ?: emptyList()
            } catch (e: Exception) {
                _error.value = e.message ?: "An error occurred"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearAnalytics() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                analyticsRepository.clearAnalytics()
                loadAnalytics()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to clear analytics"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun exportAnalyticsData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                
                val filePath = analyticsRepository.exportAnalytics(
                    startTime = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
                    endTime = System.currentTimeMillis()
                )
                
                _exportedFile.value = filePath
            } catch (e: Exception) {
                _error.value = "Failed to export analytics: ${e.message}"
                _exportedFile.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}