package com.gscube.smsbulker.repository.impl

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.gscube.smsbulker.data.model.User
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.UserRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@ContributesBinding(AppScope::class)
class UserRepositoryImpl @Inject constructor(
    @Named("applicationContext") private val context: Context
) : UserRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    override suspend fun getCurrentUser(): User {
        val userJson = prefs.getString(KEY_USER, null)
        return if (userJson != null) {
            try {
                gson.fromJson(userJson, User::class.java)
            } catch (e: Exception) {
                createDefaultUser()
            }
        } else {
            createDefaultUser()
        }
    }

    override suspend fun updateUser(user: User) {
        prefs.edit().apply {
            putString(KEY_USER, gson.toJson(user))
            apply()
        }
    }

    private fun createDefaultUser() = User(
        id = "1",
        name = "Test User",
        email = "test@example.com",
        companyName = "Test Company",
        companyAlias = "TESTCO",
        phone = "",
        credits = 0,
        isActive = true
    )

    companion object {
        private const val KEY_USER = "key_user"
    }
}