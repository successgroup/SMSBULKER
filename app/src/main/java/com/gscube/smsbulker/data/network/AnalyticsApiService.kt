package com.gscube.smsbulker.data.network

import retrofit2.Response
import retrofit2.http.GET

interface AnalyticsApiService {
    @GET("analytics/response-time")
    suspend fun getAverageResponseTime(): Response<Long>
}
