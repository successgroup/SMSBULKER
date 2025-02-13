package com.gscube.smsbulker.data.network

data class SmsApiResponse(
    val status: String,
    val message: String,
    val data: SmsApiData?
)

data class SmsApiData(
    val messageId: String?,
    val to: String?,
    val status: String?,
    val cost: Double?,
    val units: Int?
)
