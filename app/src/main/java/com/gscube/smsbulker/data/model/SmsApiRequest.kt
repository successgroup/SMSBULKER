package com.gscube.smsbulker.data.model

data class SmsApiRequest(
    val message: String,
    val sender: String,
    val recipients: List<String>
)
