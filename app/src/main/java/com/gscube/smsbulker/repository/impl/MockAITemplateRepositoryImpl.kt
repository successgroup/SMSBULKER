package com.gscube.smsbulker.repository.impl

import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.AITemplateRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock implementation of AITemplateRepository for development and testing.
 * This simulates AI-based template generation without requiring an actual AI model.
 */
@Singleton
@ContributesBinding(AppScope::class)
class MockAITemplateRepositoryImpl @Inject constructor() : AITemplateRepository {

    override suspend fun generateTemplate(
        prompt: String,
        category: TemplateCategory?
    ): MessageTemplate = withContext(Dispatchers.IO) {
        // Simulate network delay
        delay(1500)
        
        // Generate a template based on the prompt and category
        val actualCategory = category ?: suggestCategory(prompt)
        val title = generateTitle(prompt, actualCategory)
        val content = generateContent(prompt, actualCategory)
        val variables = extractVariables(content)
        
        return@withContext MessageTemplate(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content,
            category = actualCategory,
            variables = variables,
            isCustom = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    // Primary implementation of extractVariables
    override suspend fun extractVariables(content: String): List<String> {
        val variables = mutableSetOf<String>()
        
        // Extract variables in curly braces format: {variable}
        val curlyBraceRegex = "\\{([^{}]+)\\}".toRegex()
        curlyBraceRegex.findAll(content).forEach { matchResult ->
            variables.add(matchResult.groupValues[1])
        }
        
        // Extract variables in Arkesel format: %variable%
        val arkeselRegex = "%([^%]+)%".toRegex()
        arkeselRegex.findAll(content).forEach { matchResult ->
            variables.add(matchResult.groupValues[1])
        }
        
        return variables.toList()
    }
    
    // This is the primary implementation of improveTemplate
    override suspend fun improveTemplate(
        template: MessageTemplate,
        prompt: String?
    ): MessageTemplate = withContext(Dispatchers.IO) {
        // Simulate network delay
        delay(1500)
        
        // Improve the template content
        val improvedContent = improveContent(template.content, prompt, template.category)
        val variables = extractVariables(improvedContent)
        
        return@withContext template.copy(
            content = improvedContent,
            variables = variables,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun suggestCategory(prompt: String): TemplateCategory {
        val promptLower = prompt.lowercase()
        return when {
            promptLower.contains("church") || promptLower.contains("worship") || 
                    promptLower.contains("prayer") || promptLower.contains("service") -> TemplateCategory.CHURCH
            
            promptLower.contains("school") || promptLower.contains("class") || 
                    promptLower.contains("student") || promptLower.contains("teacher") -> TemplateCategory.SCHOOL
            
            promptLower.contains("bank") || promptLower.contains("account") || 
                    promptLower.contains("transaction") || promptLower.contains("credit") -> TemplateCategory.BANK
            
            promptLower.contains("club") || promptLower.contains("member") || 
                    promptLower.contains("meeting") || promptLower.contains("event") -> TemplateCategory.CLUB
            
            promptLower.contains("business") || promptLower.contains("company") || 
                    promptLower.contains("client") || promptLower.contains("service") -> TemplateCategory.BUSINESS
            
            promptLower.contains("marketing") || promptLower.contains("promotion") || 
                    promptLower.contains("discount") || promptLower.contains("offer") -> TemplateCategory.MARKETTING
            
            promptLower.contains("notification") || promptLower.contains("inform") || 
                    promptLower.contains("update") -> TemplateCategory.NOTIFICATIONS
            
            promptLower.contains("reminder") || promptLower.contains("remember") || 
                    promptLower.contains("don't forget") -> TemplateCategory.REMINDERS
            
            promptLower.contains("alert") || promptLower.contains("warning") || 
                    promptLower.contains("urgent") || promptLower.contains("emergency") -> TemplateCategory.ALERT
            
            else -> TemplateCategory.GENERAL
        }
    }

    private fun generateTitle(prompt: String, category: TemplateCategory): String {
        // Extract a concise title from the prompt
        val words = prompt.split(" ")
        val titleWords = if (words.size <= 5) words else words.subList(0, 5)
        val baseTitle = titleWords.joinToString(" ").capitalize()
        
        return when (category) {
            TemplateCategory.CHURCH -> "Church: $baseTitle"
            TemplateCategory.SCHOOL -> "School: $baseTitle"
            TemplateCategory.BANK -> "Bank: $baseTitle"
            TemplateCategory.CLUB -> "Club: $baseTitle"
            TemplateCategory.BUSINESS -> "Business: $baseTitle"
            TemplateCategory.MARKETTING -> "Marketing: $baseTitle"
            TemplateCategory.NOTIFICATIONS -> "Notification: $baseTitle"
            TemplateCategory.REMINDERS -> "Reminder: $baseTitle"
            TemplateCategory.ALERT -> "Alert: $baseTitle"
            else -> baseTitle
        }
    }

    private fun generateContent(prompt: String, category: TemplateCategory): String {
        // Generate template content based on the prompt and category
        val promptLower = prompt.lowercase()
        
        return when (category) {
            TemplateCategory.CHURCH -> generateChurchTemplate(promptLower)
            TemplateCategory.SCHOOL -> generateSchoolTemplate(promptLower)
            TemplateCategory.BANK -> generateBankTemplate(promptLower)
            TemplateCategory.CLUB -> generateClubTemplate(promptLower)
            TemplateCategory.BUSINESS -> generateBusinessTemplate(promptLower)
            TemplateCategory.MARKETTING -> generateMarketingTemplate(promptLower)
            TemplateCategory.NOTIFICATIONS -> generateNotificationTemplate(promptLower)
            TemplateCategory.REMINDERS -> generateReminderTemplate(promptLower)
            TemplateCategory.ALERT -> generateAlertTemplate(promptLower)
            else -> generateGeneralTemplate(promptLower)
        }
    }

    private fun improveContent(content: String, prompt: String?, category: TemplateCategory): String {
        // If no additional prompt is provided, make some generic improvements
        if (prompt.isNullOrBlank()) {
            // Add personalization if not present
            if (!content.contains("{name}") && !content.contains("%name%")) {
                return "Dear {name},\n\n$content\n\nBest regards,\n{sender}"
            }
            
            // Add a call to action if not present
            if (!content.lowercase().contains("call") && !content.lowercase().contains("contact") && 
                !content.lowercase().contains("reply") && !content.lowercase().contains("respond")) {
                return "$content\n\nPlease contact us at {contact_number} for more information."
            }
            
            return content
        }
        
        // Use the prompt to guide improvements
        val promptLower = prompt.lowercase()
        return when {
            promptLower.contains("shorter") || promptLower.contains("brief") -> {
                // Create a shorter version
                val sentences = content.split(".").filter { it.isNotBlank() }
                if (sentences.size <= 2) return content
                sentences.subList(0, sentences.size / 2).joinToString(".") + "."
            }
            
            promptLower.contains("longer") || promptLower.contains("detailed") -> {
                // Add more details based on category
                "$content\n\n${addCategorySpecificDetails(category)}"
            }
            
            promptLower.contains("formal") -> {
                // Make more formal
                content.replace("Hi", "Dear")
                    .replace("Hey", "Dear")
                    .replace("Thanks", "Thank you")
                    .replace("Bye", "Best regards")
            }
            
            promptLower.contains("friendly") || promptLower.contains("casual") -> {
                // Make more friendly
                content.replace("Dear", "Hi")
                    .replace("Sincerely", "Cheers")
                    .replace("Best regards", "Thanks")
            }
            
            else -> content
        }
    }
    
    private fun addCategorySpecificDetails(category: TemplateCategory): String {
        return when (category) {
            TemplateCategory.CHURCH -> "We look forward to seeing you at our upcoming service on {service_date} at {service_time}."
            TemplateCategory.SCHOOL -> "For any academic inquiries, please contact our office at {school_contact}."
            TemplateCategory.BANK -> "For security reasons, please do not share your account details with anyone."
            TemplateCategory.CLUB -> "Don't forget to check our website {website_url} for upcoming events."
            TemplateCategory.BUSINESS -> "Our business hours are {business_hours}. We look forward to serving you."
            TemplateCategory.MARKETTING -> "This offer is valid until {expiry_date}. Terms and conditions apply."
            TemplateCategory.NOTIFICATIONS -> "You can update your notification preferences at any time through our app."
            TemplateCategory.REMINDERS -> "Set a reminder on your calendar to avoid missing this important date."
            TemplateCategory.ALERT -> "Please take immediate action as this requires your urgent attention."
            else -> "Thank you for your attention to this message."
        }
    }
    
    private fun generateGeneralTemplate(prompt: String): String {
        return if (prompt.contains("announcement")) {
            "Dear {name},\n\nWe are pleased to announce {announcement_details}.\n\nFor more information, please contact us at {contact_number}.\n\nBest regards,\n{organization_name}"
        } else if (prompt.contains("invitation")) {
            "Dear {name},\n\nYou are cordially invited to {event_name} on {event_date} at {event_location}.\n\nPlease RSVP by {rsvp_date}.\n\nBest regards,\n{sender}"
        } else {
            "Dear {name},\n\nThank you for your interest in our services. We would like to inform you about {information_details}.\n\nIf you have any questions, please don't hesitate to contact us at {contact_number}.\n\nBest regards,\n{sender}"
        }
    }
    
    private fun generateChurchTemplate(prompt: String): String {
        return when {
            prompt.contains("service") -> {
                "Dear {name},\n\nWe invite you to join us for our {service_type} service on {service_date} at {service_time}.\n\n{service_details}\n\nBlessings,\n{church_name}"
            }
            prompt.contains("event") -> {
                "Dear {name},\n\nWe are excited to announce our upcoming {event_name} on {event_date} at {event_location}.\n\n{event_details}\n\nWe hope to see you there!\n\nBlessings,\n{church_name}"
            }
            prompt.contains("prayer") -> {
                "Dear {name},\n\nWe invite you to join our prayer meeting on {prayer_date} at {prayer_time}.\n\nLet us come together in prayer for {prayer_purpose}.\n\nBlessings,\n{church_name}"
            }
            else -> {
                "Dear {name},\n\nGrace and peace to you from God our Father. We would like to inform you about {message_content}.\n\nFor more information, please contact {contact_person} at {contact_number}.\n\nBlessings,\n{church_name}"
            }
        }
    }
    
    private fun generateSchoolTemplate(prompt: String): String {
        return when {
            prompt.contains("meeting") -> {
                "Dear {parent_name},\n\nWe would like to invite you to a parent-teacher meeting on {meeting_date} at {meeting_time} to discuss {student_name}'s progress.\n\nPlease confirm your attendance by replying to this message.\n\nRegards,\n{teacher_name}\n{school_name}"
            }
            prompt.contains("event") -> {
                "Dear {parent_name},\n\nWe are pleased to invite you to our {event_name} on {event_date} at {event_time}.\n\n{event_details}\n\nWe look forward to your participation.\n\nRegards,\n{school_name}"
            }
            prompt.contains("exam") || prompt.contains("test") -> {
                "Dear {parent_name},\n\nThis is to inform you that {student_name} will have {exam_type} exams from {exam_start_date} to {exam_end_date}.\n\nPlease ensure that your child prepares adequately for these exams.\n\nRegards,\n{teacher_name}\n{school_name}"
            }
            else -> {
                "Dear {parent_name},\n\nThis is to inform you about {information_details} regarding {student_name}.\n\nFor any clarification, please contact us at {school_contact}.\n\nRegards,\n{school_name}"
            }
        }
    }
    
    private fun generateBankTemplate(prompt: String): String {
        return when {
            prompt.contains("transaction") -> {
                "Dear {customer_name},\n\nA {transaction_type} of {amount} has been {transaction_action} your account {account_number} on {transaction_date} at {transaction_time}.\n\nYour current balance is {current_balance}.\n\nIf you did not authorize this transaction, please contact our customer service immediately at {bank_contact}.\n\nRegards,\n{bank_name}"
            }
            prompt.contains("statement") -> {
                "Dear {customer_name},\n\nYour account statement for {account_number} for the period {statement_start_date} to {statement_end_date} is now available.\n\nPlease log in to your online banking or visit your nearest branch to access your statement.\n\nRegards,\n{bank_name}"
            }
            prompt.contains("loan") -> {
                "Dear {customer_name},\n\nYour loan application {application_id} has been {application_status}.\n\n{additional_details}\n\nFor more information, please contact our loan department at {loan_department_contact}.\n\nRegards,\n{bank_name}"
            }
            else -> {
                "Dear {customer_name},\n\nThis is to inform you about {information_details} regarding your account {account_number}.\n\nFor any assistance, please contact our customer service at {bank_contact}.\n\nRegards,\n{bank_name}"
            }
        }
    }
    
    private fun generateClubTemplate(prompt: String): String {
        return when {
            prompt.contains("meeting") -> {
                "Dear {member_name},\n\nThis is a reminder about our upcoming meeting on {meeting_date} at {meeting_time} at {meeting_location}.\n\nAgenda: {meeting_agenda}\n\nPlease confirm your attendance by replying to this message.\n\nRegards,\n{club_name}"
            }
            prompt.contains("event") -> {
                "Dear {member_name},\n\nWe are excited to announce our {event_name} on {event_date} at {event_location}.\n\n{event_details}\n\nPlease RSVP by {rsvp_date}.\n\nRegards,\n{club_name}"
            }
            prompt.contains("membership") -> {
                "Dear {member_name},\n\nYour membership with {club_name} is due for renewal on {renewal_date}.\n\nPlease complete the renewal process by {renewal_deadline} to continue enjoying your membership benefits.\n\nRegards,\n{club_name}"
            }
            else -> {
                "Dear {member_name},\n\nWe would like to inform you about {information_details}.\n\nFor more information, please contact us at {club_contact}.\n\nRegards,\n{club_name}"
            }
        }
    }
    
    private fun generateBusinessTemplate(prompt: String): String {
        return when {
            prompt.contains("appointment") -> {
                "Dear {client_name},\n\nThis is to confirm your appointment with {staff_name} on {appointment_date} at {appointment_time}.\n\nPlease arrive 10 minutes early. If you need to reschedule, please contact us at {business_contact}.\n\nRegards,\n{business_name}"
            }
            prompt.contains("invoice") -> {
                "Dear {client_name},\n\nYour invoice {invoice_number} for {service_details} amounting to {invoice_amount} has been generated on {invoice_date}.\n\nPayment is due by {payment_due_date}. Please make your payment to {payment_details}.\n\nRegards,\n{business_name}"
            }
            prompt.contains("quote") || prompt.contains("quotation") -> {
                "Dear {client_name},\n\nAs requested, here is your quotation for {service_details}:\n\nTotal Amount: {quote_amount}\nValidity: {quote_validity}\n\nPlease contact us at {business_contact} to proceed with this quotation.\n\nRegards,\n{business_name}"
            }
            else -> {
                "Dear {client_name},\n\nThank you for choosing {business_name}. We would like to inform you about {information_details}.\n\nFor any inquiries, please contact us at {business_contact}.\n\nRegards,\n{business_name}"
            }
        }
    }
    
    private fun generateMarketingTemplate(prompt: String): String {
        return when {
            prompt.contains("sale") || prompt.contains("discount") -> {
                "Dear {customer_name},\n\nDon't miss our {sale_name} with discounts up to {discount_percentage}% off on {product_category}!\n\nSale Period: {sale_start_date} to {sale_end_date}\nLocation: {store_location}\n\nHurry, limited stock available!\n\nRegards,\n{business_name}"
            }
            prompt.contains("launch") || prompt.contains("new product") -> {
                "Dear {customer_name},\n\nWe are excited to announce the launch of our new {product_name}!\n\nLaunch Date: {launch_date}\nSpecial Offer: {special_offer}\n\nBe among the first to experience it!\n\nRegards,\n{business_name}"
            }
            prompt.contains("promotion") -> {
                "Dear {customer_name},\n\nTake advantage of our special promotion: {promotion_details}\n\nValid from {promotion_start_date} to {promotion_end_date}.\n\nUse code {promo_code} at checkout.\n\nRegards,\n{business_name}"
            }
            else -> {
                "Dear {customer_name},\n\nWe have something special for you! {offer_details}\n\nValid until {offer_validity}.\n\nDon't miss this opportunity!\n\nRegards,\n{business_name}"
            }
        }
    }
    
    private fun generateNotificationTemplate(prompt: String): String {
        return when {
            prompt.contains("update") -> {
                "Dear {user_name},\n\nWe have updated our {update_subject} on {update_date}.\n\n{update_details}\n\nFor more information, please visit {website_url}.\n\nRegards,\n{organization_name}"
            }
            prompt.contains("account") -> {
                "Dear {user_name},\n\nThis is to notify you that your account {account_action} on {action_date}.\n\n{additional_details}\n\nIf you have any questions, please contact us at {contact_email}.\n\nRegards,\n{organization_name}"
            }
            prompt.contains("delivery") || prompt.contains("shipment") -> {
                "Dear {customer_name},\n\nYour order {order_number} has been {delivery_status} on {delivery_date}.\n\nTracking Number: {tracking_number}\nEstimated Delivery: {estimated_delivery}\n\nFor any inquiries, please contact our customer service at {customer_service_contact}.\n\nRegards,\n{company_name}"
            }
            else -> {
                "Dear {user_name},\n\nThis is to notify you about {notification_subject}.\n\n{notification_details}\n\nFor more information, please contact us at {contact_details}.\n\nRegards,\n{organization_name}"
            }
        }
    }
    
    private fun generateReminderTemplate(prompt: String): String {
        return when {
            prompt.contains("appointment") -> {
                "Dear {client_name},\n\nThis is a reminder of your appointment on {appointment_date} at {appointment_time} with {appointment_with}.\n\nLocation: {appointment_location}\n\nIf you need to reschedule, please contact us at {contact_number}.\n\nRegards,\n{organization_name}"
            }
            prompt.contains("payment") -> {
                "Dear {customer_name},\n\nThis is a friendly reminder that your payment of {payment_amount} for {payment_purpose} is due on {payment_due_date}.\n\nPlease make your payment to {payment_details}.\n\nRegards,\n{organization_name}"
            }
            prompt.contains("event") -> {
                "Dear {attendee_name},\n\nThis is a reminder about the {event_name} scheduled for {event_date} at {event_time}.\n\nLocation: {event_location}\n\nWe look forward to your participation.\n\nRegards,\n{organization_name}"
            }
            else -> {
                "Dear {recipient_name},\n\nThis is a reminder about {reminder_subject} on {reminder_date}.\n\n{reminder_details}\n\nPlease take note of this important reminder.\n\nRegards,\n{sender_name}"
            }
        }
    }
    
    private fun generateAlertTemplate(prompt: String): String {
        return when {
            prompt.contains("security") -> {
                "ALERT: {security_issue} detected on your account on {detection_date} at {detection_time}.\n\nIf this was not you, please contact our security team immediately at {security_contact}.\n\nRegards,\n{organization_name}"
            }
            prompt.contains("emergency") -> {
                "EMERGENCY ALERT: {emergency_details}\n\nLocation: {emergency_location}\nTime: {emergency_time}\n\nPlease follow the emergency protocol and contact {emergency_contact} immediately.\n\n{organization_name}"
            }
            prompt.contains("weather") -> {
                "WEATHER ALERT: {weather_condition} expected in {location} on {date} from {start_time} to {end_time}.\n\nPlease take necessary precautions and stay safe.\n\n{weather_authority}"
            }
            else -> {
                "URGENT: {alert_subject}\n\n{alert_details}\n\nPlease take immediate action. For assistance, contact {contact_details}.\n\n{organization_name}"
            }
        }
    }

    // Duplicate implementations removed to fix errors
    // The primary implementations are at the top of the file
    
    // Duplicate method implementations removed
    // The primary implementations are at the top of the file
    
    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}