package com.gscube.smsbulker.utils

object PhoneNumberValidator {
    
    // Ghana network codes
    private val GHANA_NETWORK_CODES = setOf(
        "020", "023", "024", "025", "026", "027", "028", "029", // Vodafone, MTN, AirtelTigo
        "050", "053", "054", "055", "056", "057", "058", "059", // MTN, AirtelTigo, Vodafone
        "030", "031", "032" // Fixed lines
    )
    
    private val GHANA_COUNTRY_CODE = "233"
    
    /**
     * Validates if a phone number is a valid Ghana number
     * Accepts formats: 0201234567, +233201234567, 233201234567
     */
    fun isValidGhanaNumber(phoneNumber: String): Boolean {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        
        // Check if the number is empty after cleaning
        if (cleanNumber.isEmpty()) return false
        
        // Check if the number starts with Ghana country code or 0
        return when {
            // Format: 233XXXXXXXXX (10 digits after country code)
            cleanNumber.startsWith(GHANA_COUNTRY_CODE) -> {
                val networkCode = "0" + cleanNumber.substring(3, 5)
                cleanNumber.length == 12 && GHANA_NETWORK_CODES.contains(networkCode)
            }
            // Format: 0XXXXXXXXX (10 digits total)
            cleanNumber.startsWith("0") -> {
                val networkCode = cleanNumber.substring(0, 3)
                cleanNumber.length == 10 && GHANA_NETWORK_CODES.contains(networkCode)
            }
            else -> false
        }
    }
    
    /**
     * Formats a phone number to the standard Ghana format with country code
     * Returns null if the number is invalid
     */
    fun formatGhanaNumber(phoneNumber: String): String? {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        
        if (!isValidGhanaNumber(cleanNumber)) return null
        
        return when {
            cleanNumber.startsWith(GHANA_COUNTRY_CODE) -> cleanNumber
            cleanNumber.startsWith("0") -> GHANA_COUNTRY_CODE + cleanNumber.substring(1)
            else -> null
        }
    }
    
    /**
     * Removes all non-digit characters from a phone number
     * Also removes the + sign from the beginning if present
     */
    private fun cleanPhoneNumber(phoneNumber: String): String {
        return phoneNumber.trim()
            .replace("\\s".toRegex(), "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .let { if (it.startsWith("+")) it.substring(1) else it }
    }
}