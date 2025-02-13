package com.gscube.smsbulker.utils

import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory

object SampleTemplates {
    val generalTemplates = listOf(
        MessageTemplate(
            title = "General Announcement",
            content = "Dear {name}, {message}. Thank you for your attention.",
            category = TemplateCategory.GENERAL,
            variables = listOf("{name}", "{message}"),
            isCustom = false
        ),
        MessageTemplate(
            title = "General Update",
            content = "Hello {name}, this is to inform you that {update}.",
            category = TemplateCategory.GENERAL,
            variables = listOf("{name}", "{update}"),
            isCustom = false
        )
    )

    val churchTemplates = listOf(
        MessageTemplate(
            title = "Sunday Service Reminder",
            content = "Dear {name}, join us this Sunday at {time} for our worship service. Theme: {theme}. God bless you!",
            category = TemplateCategory.CHURCH,
            variables = listOf("{name}", "{time}", "{theme}"),
            isCustom = false
        ),
        MessageTemplate(
            title = "Prayer Meeting",
            content = "Dear {name}, you're invited to our prayer meeting on {date} at {time}. Let's seek God together!",
            category = TemplateCategory.CHURCH,
            variables = listOf("{name}", "{date}", "{time}"),
            isCustom = false
        )
    )

    val schoolTemplates = listOf(
        MessageTemplate(
            title = "Fee Reminder",
            content = "Dear Parent/Guardian, kindly note that {student}'s school fees of {amount} is due by {date}. Thank you.",
            category = TemplateCategory.SCHOOL,
            variables = listOf("{student}", "{amount}", "{date}"),
            isCustom = false
        ),
        MessageTemplate(
            title = "School Event",
            content = "Dear Parent, {school} invites you to {event} on {date} at {time}. Your presence is important.",
            category = TemplateCategory.SCHOOL,
            variables = listOf("{school}", "{event}", "{date}", "{time}"),
            isCustom = false
        )
    )

    val bankTemplates = listOf(
        MessageTemplate(
            title = "Transaction Alert",
            content = "Dear {name}, a {type} of {amount} occurred on your account. Balance: {balance}. Date: {date}",
            category = TemplateCategory.BANK,
            variables = listOf("{name}", "{type}", "{amount}", "{balance}", "{date}"),
            isCustom = false
        ),
        MessageTemplate(
            title = "Account Update",
            content = "Dear {name}, your account {update}. If you have any questions, please contact us.",
            category = TemplateCategory.BANK,
            variables = listOf("{name}", "{update}"),
            isCustom = false
        )
    )

    val clubTemplates = listOf(
        MessageTemplate(
            title = "Meeting Reminder",
            content = "Dear {name}, reminder for our club meeting on {date} at {time}. Agenda: {agenda}",
            category = TemplateCategory.CLUB,
            variables = listOf("{name}", "{date}", "{time}", "{agenda}"),
            isCustom = false
        ),
        MessageTemplate(
            title = "Event Invitation",
            content = "Hi {name}, you're invited to our {event} on {date}. Don't miss out!",
            category = TemplateCategory.CLUB,
            variables = listOf("{name}", "{event}", "{date}"),
            isCustom = false
        )
    )

    val businessTemplates = listOf(
        MessageTemplate(
            title = "Business Update",
            content = "Dear {name}, {company} would like to inform you that {update}.",
            category = TemplateCategory.BUSINESS,
            variables = listOf("{name}", "{company}", "{update}"),
            isCustom = false
        ),
        MessageTemplate(
            title = "Meeting Schedule",
            content = "Dear {name}, your meeting with {company} is scheduled for {date} at {time}.",
            category = TemplateCategory.BUSINESS,
            variables = listOf("{name}", "{company}", "{date}", "{time}"),
            isCustom = false
        )
    )

    val marketingTemplates = listOf(
        MessageTemplate(
            title = "Special Offer",
            content = "Hi {name}! Don't miss our {offer} - {details}. Valid until {date}.",
            category = TemplateCategory.MARKETTING,
            variables = listOf("{name}", "{offer}", "{details}", "{date}"),
            isCustom = false
        ),
        MessageTemplate(
            title = "Product Launch",
            content = "Dear {name}, introducing our new {product}! {description}. Learn more at {link}",
            category = TemplateCategory.MARKETTING,
            variables = listOf("{name}", "{product}", "{description}", "{link}"),
            isCustom = false
        )
    )

    val notificationTemplates = listOf(
        MessageTemplate(
            title = "Order Confirmation",
            content = "Thank you {name} for your order #{orderNumber}. Your {product} will be delivered by {date}.",
            category = TemplateCategory.NOTIFICATIONS,
            variables = listOf("{name}", "{orderNumber}", "{product}", "{date}"),
            isCustom = false
        ),
        MessageTemplate(
            title = "Status Update",
            content = "Hi {name}, your {item} status has been updated to: {status}",
            category = TemplateCategory.NOTIFICATIONS,
            variables = listOf("{name}", "{item}", "{status}"),
            isCustom = false
        )
    )

    val reminderTemplates = listOf(
        MessageTemplate(
            title = "Appointment Reminder",
            content = "Dear {name}, this is a reminder of your appointment on {date} at {time}.",
            category = TemplateCategory.REMINDERS,
            variables = listOf("{name}", "{date}", "{time}"),
            isCustom = false
        ),
        MessageTemplate(
            title = "Task Due",
            content = "Hi {name}, your task '{task}' is due on {date}. Status: {status}",
            category = TemplateCategory.REMINDERS,
            variables = listOf("{name}", "{task}", "{date}", "{status}"),
            isCustom = false
        )
    )

    val alertTemplates = listOf(
        MessageTemplate(
            title = "Security Alert",
            content = "Dear {name}, we detected {activity} on your account at {time}. If this wasn't you, please contact us.",
            category = TemplateCategory.ALERT,
            variables = listOf("{name}", "{activity}", "{time}"),
            isCustom = false
        ),
        MessageTemplate(
            title = "System Alert",
            content = "Alert: {system} status is {status}. Action required: {action}",
            category = TemplateCategory.ALERT,
            variables = listOf("{system}", "{status}", "{action}"),
            isCustom = false
        )
    )

    fun getAllTemplates(): List<MessageTemplate> {
        return generalTemplates + 
               churchTemplates + 
               schoolTemplates + 
               bankTemplates + 
               clubTemplates + 
               businessTemplates + 
               marketingTemplates + 
               notificationTemplates + 
               reminderTemplates + 
               alertTemplates
    }
}