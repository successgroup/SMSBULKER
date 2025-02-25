package com.gscube.smsbulker.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ArkeselSmsRequest(
    @Json(name = "sender") val sender: String,
    @Json(name = "message") val message: String,
    @Json(name = "recipients") val recipients: Map<String, Map<String, String>>,
    @Json(name = "sandbox") val sandbox: Boolean = false,
    @Json(name = "api_key") val apiKey: String,
    @Json(name = "schedule_time") val scheduleTime: String? = null,
    @Json(name = "callback_url") val callbackUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class ArkeselSmsResponse(
    @Json(name = "status") val status: String,
    @Json(name = "code") val code: Int,
    @Json(name = "message") val message: String,
    @Json(name = "data") val data: ArkeselSmsResponseData? = null
)

@JsonClass(generateAdapter = true)
data class ArkeselSmsResponseData(
    @Json(name = "request_id") val requestId: String,
    @Json(name = "message_id") val messageId: String,
    @Json(name = "status") val status: String,
    @Json(name = "cost") val cost: Double,
    @Json(name = "credits_used") val creditsUsed: Int
)
