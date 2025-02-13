package com.gscube.smsbulker.data.model

data class SmsRequest(
    val action: String = "send-sms",
    val api_key: String,
    val to: String,
    val from: String,
    val sms: String
)

data class SmsResponse(
    val code: String,
    val message: String,
    val balance: Int,
    val main_balance: Double,
    val user: String
)
