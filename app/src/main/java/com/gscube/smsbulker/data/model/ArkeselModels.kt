package com.gscube.smsbulker.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ArkeselSmsRequest(
    @Json(name = "sender") val sender: String,
    @Json(name = "message") val message: String,
    @Json(name = "recipients") val recipients: Map<String, Map<String, String>>,
    @Json(name = "sandbox") val sandbox: Boolean = false,
    @Json(name = "schedule_time") val scheduleTime: String? = null,
    @Json(name = "callback_url") val callbackUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class ArkeselSmsResponse(
    @Json(name = "status") val status: String,
    @Json(name = "data") val data: List<ArkeselMessageResult>
)

@JsonClass(generateAdapter = true)
data class ArkeselMessageResult(
    @Json(name = "recipient") val recipient: String,
    @Json(name = "id") val id: String
)

@JsonClass(generateAdapter = true)
data class DeliveryReport(
    @Json(name = "message_id") val messageId: String,
    @Json(name = "recipient") val recipient: String,
    @Json(name = "status") val status: String, // "delivered", "failed", "pending"
    @Json(name = "timestamp") val timestamp: Long,
    @Json(name = "error_code") val errorCode: String? = null,
    @Json(name = "error_message") val errorMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class ArkeselAnalyticsRequest(
    @Json(name = "start_date") val startDate: String,
    @Json(name = "end_date") val endDate: String,
    @Json(name = "message_ids") val messageIds: List<String>? = null
)
