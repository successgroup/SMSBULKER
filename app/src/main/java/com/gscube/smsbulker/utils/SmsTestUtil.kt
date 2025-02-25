package com.gscube.smsbulker.utils

import com.gscube.smsbulker.repository.SmsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object SmsTestUtil {
    fun testSendSms(
        smsRepository: SmsRepository,
        recipient: String,
        message: String = "Test message from SMSBULKER",
        sender: String? = null
    ): String {
        return runBlocking {
            try {
                val result = smsRepository.sendSingleSms(
                    recipient = recipient,
                    message = message,
                    sender = sender
                ).first()

                return@runBlocking when {
                    result.status.equals("success", ignoreCase = true) -> 
                        "Message sent successfully!\nMessage ID: ${result.messageId}\nStatus: ${result.status}"
                    else -> 
                        "Failed to send message.\nStatus: ${result.status}"
                }
            } catch (e: Exception) {
                return@runBlocking "Error sending message: ${e.message}"
            }
        }
    }

    fun testBulkSms(
        smsRepository: SmsRepository,
        recipients: List<String>,
        message: String = "Test bulk message from SMSBULKER",
        sender: String? = null
    ): String {
        return runBlocking {
            try {
                val results = smsRepository.sendBulkSms(
                    recipients = recipients,
                    message = message,
                    sender = sender
                ).first()

                return@runBlocking buildString {
                    appendLine("Bulk SMS Results:")
                    results.forEach { result ->
                        appendLine("Recipient: ${result.recipient}")
                        appendLine("Status: ${result.status}")
                        appendLine("Message ID: ${result.messageId}")
                        appendLine("---")
                    }
                }
            } catch (e: Exception) {
                return@runBlocking "Error sending bulk messages: ${e.message}"
            }
        }
    }

    fun testPersonalizedSms(
        smsRepository: SmsRepository,
        recipientsWithVars: List<Pair<String, Map<String, String>>>,
        messageTemplate: String = "Hello {name}, your balance is {amount}",
        sender: String? = null
    ): String {
        return runBlocking {
            try {
                val results = smsRepository.sendPersonalizedSms(
                    recipients = recipientsWithVars,
                    messageTemplate = messageTemplate,
                    sender = sender
                ).first()

                return@runBlocking buildString {
                    appendLine("Personalized SMS Results:")
                    results.forEach { result ->
                        appendLine("Recipient: ${result.recipient}")
                        appendLine("Status: ${result.status}")
                        appendLine("Message ID: ${result.messageId}")
                        appendLine("---")
                    }
                }
            } catch (e: Exception) {
                return@runBlocking "Error sending personalized messages: ${e.message}"
            }
        }
    }
}
