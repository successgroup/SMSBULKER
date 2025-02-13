package com.gscube.smsbulker.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecureStorage(context: Context) {
    companion object {
        private const val PREFS_FILENAME = "secure_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_EMAIL = "email"
        private const val KEY_AUTH_STATE = "auth_state"
    }

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs = EncryptedSharedPreferences.create(
        PREFS_FILENAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @Synchronized
    fun saveAuthData(userId: String, apiKey: String, email: String) {
        prefs.edit().apply {
            // Clear any existing data first
            clear()
            // Save new data
            putString(KEY_USER_ID, userId)
            putString(KEY_API_KEY, apiKey)
            putString(KEY_EMAIL, email)
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