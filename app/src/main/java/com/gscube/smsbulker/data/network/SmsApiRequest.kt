package com.gscube.smsbulker.data.network

data class SmsApiRequest(
    val to: List<String>,
    val message: String,
    val sender: String,
    val apiKey: String
) {
    companion object {
        fun create(to: List<String>, message: String, from: String): SmsApiRequest {
            return SmsApiRequest(
                to = to,
                message = message,
                sender = from,
                apiKey = "" // Will be set by the service
            )
        }
    }
}
