package com.gscube.smsbulker.data

data class MessageAnalytics(
    val totalSent: Int = 0,
    val delivered: Int = 0,
    val failed: Int = 0,
    val pending: Int = 0,
    val creditsUsed: Int = 0,
    val periodStart: Long = 0,
    val periodEnd: Long = System.currentTimeMillis()
) 