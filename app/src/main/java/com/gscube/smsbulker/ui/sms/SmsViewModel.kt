package com.gscube.smsbulker.ui.sms

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.BulkSmsResult
import com.gscube.smsbulker.data.model.SmsStatus
import com.gscube.smsbulker.repository.SmsRepository
import com.gscube.smsbulker.di.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsViewModel @Inject constructor(
    private val smsRepository: SmsRepository
) : ViewModel() {
    private val _sendStatus = MutableLiveData<SendStatus>()
    val sendStatus: LiveData<SendStatus> = _sendStatus

    fun sendBulkSms(recipients: List<String>, message: String, sender: String?) {
        viewModelScope.launch {
            _sendStatus.value = SendStatus.Loading
            try {
                smsRepository.sendBulkSms(recipients, message, sender).collect { results ->
                    val successCount = results.count { SmsStatus.valueOf(it.status) == SmsStatus.SENT }
                    val failureCount = results.size - successCount
                    
                    when {
                        failureCount == 0 -> _sendStatus.value = SendStatus.Success(successCount)
                        successCount == 0 -> _sendStatus.value = SendStatus.Error("All messages failed to send")
                        else -> _sendStatus.value = SendStatus.PartialSuccess(
                            successCount = successCount,
                            failureCount = failureCount
                        )
                    }
                }
            } catch (e: Exception) {
                _sendStatus.value = SendStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun sendPersonalizedSms(
        recipients: List<Pair<String, Map<String, String>>>,
        messageTemplate: String,
        sender: String?
    ) {
        viewModelScope.launch {
            _sendStatus.value = SendStatus.Loading
            try {
                smsRepository.sendPersonalizedSms(recipients, messageTemplate, sender).collect { results ->
                    val successCount = results.count { SmsStatus.valueOf(it.status) == SmsStatus.SENT }
                    val failureCount = results.size - successCount
                    
                    when {
                        failureCount == 0 -> _sendStatus.value = SendStatus.Success(successCount)
                        successCount == 0 -> _sendStatus.value = SendStatus.Error("All messages failed to send")
                        else -> _sendStatus.value = SendStatus.PartialSuccess(
                            successCount = successCount,
                            failureCount = failureCount
                        )
                    }
                }
            } catch (e: Exception) {
                _sendStatus.value = SendStatus.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class SendStatus {
    object Loading : SendStatus()
    data class Success(val count: Int) : SendStatus()
    data class PartialSuccess(val successCount: Int, val failureCount: Int) : SendStatus()
    data class Error(val message: String) : SendStatus()
}
