package com.gscube.smsbulker.repository

import com.gscube.smsbulker.data.model.BulkSmsResult
import kotlinx.coroutines.flow.Flow

interface SmsRepository {
    suspend fun sendSingleSms(
        recipient: String,
        message: String,
        sender: String?
    ): Flow<BulkSmsResult>

    suspend fun sendBulkSms(
        recipients: List<String>,
        message: String,
        sender: String?
    ): Flow<List<BulkSmsResult>>

    suspend fun sendPersonalizedSms(
        recipients: List<Pair<String, Map<String, String>>>,
        messageTemplate: String,
        sender: String?
    ): Flow<List<BulkSmsResult>>
}