package com.gscube.smsbulker.data.model

enum class TemplateCategory {
    GENERAL,
    CHURCH,
    SCHOOL,
    BANK,
    CLUB,
    BUSINESS,
    MARKETTING,
    NOTIFICATIONS,
    REMINDERS,
    ALERT;

    override fun toString(): String {
        return when (this) {
            GENERAL -> "General"
            CHURCH -> "Church"
            SCHOOL -> "School"
            BANK -> "Bank"
            CLUB -> "Club"
            BUSINESS -> "Business"
            MARKETTING -> "Marketing"
            NOTIFICATIONS -> "Notifications"
            REMINDERS -> "Reminders"
            ALERT -> "Alert"

        }
    }
}
