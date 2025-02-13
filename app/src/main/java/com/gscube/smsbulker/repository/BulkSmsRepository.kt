package com.gscube.smsbulker.repository

import android.net.Uri
import com.gscube.smsbulker.data.model.BatchStatus
import com.gscube.smsbulker.data.model.BulkSmsRequest
import com.gscube.smsbulker.data.model.BulkSmsResponse
import com.gscube.smsbulker.data.model.MessageStatus
import com.gscube.smsbulker.data.model.Recipient
import kotlinx.coroutines.flow.Flow

interface BulkSmsRepository {
    suspend fun sendBulkSms(request: BulkSmsRequest): Result<BulkSmsResponse>
    suspend fun getBatchStatus(batchId: String): Result<BatchStatus>
    suspend fun getBatchMessageStatuses(batchId: String): Result<List<MessageStatus>>
    fun parseRecipientsFromCsv(uri: Uri): Flow<List<Recipient>>
    suspend fun getMessageStatus(messageId: String): Result<MessageStatus>
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus): Result<Unit>
}