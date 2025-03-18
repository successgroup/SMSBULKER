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
