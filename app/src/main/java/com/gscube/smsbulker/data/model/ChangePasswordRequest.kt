package com.gscube.smsbulker.data.model

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)
