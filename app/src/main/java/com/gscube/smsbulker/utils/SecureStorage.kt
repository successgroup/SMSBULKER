package com.gscube.smsbulker.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.gscube.smsbulker.data.UserProfile
import com.google.firebase.Timestamp
import java.util.Date

class SecureStorage(context: Context) {
    companion object {
        private const val PREFS_FILENAME = "secure_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
        private const val KEY_COMPANY = "company"  // Changed from KEY_COMPANY_NAME
        private const val KEY_COMPANY_ALIAS = "company_alias"
        private const val KEY_PHONE = "phone"
        private const val KEY_CREDITS = "credits"
        private const val KEY_IS_ACTIVE = "is_active"
        private const val KEY_AUTH_STATE = "auth_state"
        private const val KEY_EMAIL_VERIFIED = "email_verified"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_LAST_LOGIN = "last_login"
    }

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs = EncryptedSharedPreferences.create(
        PREFS_FILENAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Update saveUserProfile method
    @Synchronized
    fun saveUserProfile(profile: UserProfile) {
        prefs.edit().apply {
            putString(KEY_USER_ID, profile.userId)
            putString(KEY_API_KEY, profile.apiKey)
            putString(KEY_EMAIL, profile.email)
            putString(KEY_NAME, profile.name)
            putString(KEY_COMPANY, profile.company ?: "")  // Changed from KEY_COMPANY_NAME
            putString(KEY_COMPANY_ALIAS, profile.companyAlias)  // Fixed: use profile.companyAlias
            putString(KEY_PHONE, profile.phone)
            putBoolean(KEY_EMAIL_VERIFIED, profile.emailVerified)
            putString(KEY_CREATED_AT, profile.createdAt.toDate().time.toString())
            putString(KEY_LAST_LOGIN, profile.lastLogin.toDate().time.toString())
            putBoolean(KEY_AUTH_STATE, true)
            commit()
        }
    }

    // Update getUserProfile method
    @Synchronized
    fun getUserProfile(): UserProfile? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        return UserProfile(
            userId = userId,
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            name = prefs.getString(KEY_NAME, "") ?: "",
            phone = prefs.getString(KEY_PHONE, "") ?: "",
            company = prefs.getString(KEY_COMPANY, null),  // Changed from KEY_COMPANY_NAME
            companyAlias = prefs.getString(KEY_COMPANY_ALIAS, "") ?: "",
            emailVerified = prefs.getBoolean(KEY_EMAIL_VERIFIED, false),
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            createdAt = Timestamp(Date(prefs.getString(KEY_CREATED_AT, "0")?.toLong() ?: 0)),
            lastLogin = Timestamp(Date(prefs.getString(KEY_LAST_LOGIN, "0")?.toLong() ?: 0))
        )
    }

    // Update saveAuthData method
    @Synchronized
    fun saveAuthData(
        userId: String,
        apiKey: String,
        email: String,
        name: String = "",
        company: String = "",  // Changed from companyName
        companyAlias: String = "",
        phone: String = "",
        credits: Int = 0,
        isActive: Boolean = true
    ) {
        prefs.edit().apply {
            // Clear any existing data first
            clear()
            // Save new data
            putString(KEY_USER_ID, userId)
            putString(KEY_API_KEY, apiKey)
            putString(KEY_EMAIL, email)
            putString(KEY_NAME, name)
            putString(KEY_COMPANY, company)  // Changed from KEY_COMPANY_NAME
            putString(KEY_COMPANY_ALIAS, companyAlias)
            putString(KEY_PHONE, phone)
            putInt(KEY_CREDITS, credits)
            putBoolean(KEY_IS_ACTIVE, isActive)
            putBoolean(KEY_AUTH_STATE, true)
            // Use commit() instead of apply() to ensure data is written immediately
            commit()
        }
    }

    @Synchronized
    fun saveApiKey(apiKey: String) {
        prefs.edit().apply {
            putString(KEY_API_KEY, apiKey)
            commit()
        }
    }

    @Synchronized
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    @Synchronized
    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    @Synchronized
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    @Synchronized
    fun getName(): String? = prefs.getString(KEY_NAME, null)

    @Synchronized
    fun getCompany(): String? = prefs.getString(KEY_COMPANY, null)

    @Synchronized
    fun getCompanyAlias(): String? = prefs.getString(KEY_COMPANY_ALIAS, null)

    @Synchronized
    fun getPhone(): String? = prefs.getString(KEY_PHONE, null)

    @Synchronized
    fun getCredits(): Int = prefs.getInt(KEY_CREDITS, 0)

    @Synchronized
    fun isActive(): Boolean = prefs.getBoolean(KEY_IS_ACTIVE, false)

    @Synchronized
    fun clearAuthData() {
        prefs.edit().clear().commit()
    }

    @Synchronized
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_AUTH_STATE, false) &&
               !getUserId().isNullOrEmpty() &&
               !getApiKey().isNullOrEmpty()
    }
}
