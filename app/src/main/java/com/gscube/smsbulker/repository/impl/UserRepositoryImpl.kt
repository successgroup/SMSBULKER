package com.gscube.smsbulker.repository.impl

import android.content.Context
import com.gscube.smsbulker.data.model.User
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.UserRepository
import com.gscube.smsbulker.utils.SecureStorage
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@ContributesBinding(AppScope::class)
class UserRepositoryImpl @Inject constructor(
    @Named("applicationContext") private val context: Context,
    private val secureStorage: SecureStorage
) : UserRepository {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val userAdapter = moshi.adapter(User::class.java)

    // Change this method to match the interface
    override suspend fun getCurrentUser(): User {
        val userId = secureStorage.getUserId()
        val email = secureStorage.getEmail()
        val apiKey = secureStorage.getApiKey()
        
        return if (userId != null && email != null && apiKey != null) {
            User(
                id = userId,
                email = email,
                apiKey = apiKey,
                name = secureStorage.getName() ?: "",
                company = secureStorage.getCompany(),
                companyAlias = secureStorage.getCompanyAlias() ?: "",
                phone = secureStorage.getPhone(),
                credits = secureStorage.getCredits(),
                isActive = secureStorage.isActive()
            )
        } else {
            createDefaultUser()
        }
    }

    override suspend fun updateUser(user: User) {
        secureStorage.saveAuthData(
            userId = user.id ?: "",
            apiKey = user.apiKey,
            email = user.email,
            name = user.name,
            company = user.company ?: "",  // Changed from companyName to company
            companyAlias = user.companyAlias,
            phone = user.phone ?: "",
            credits = user.credits,
            isActive = user.isActive
        )
    }

    private fun createDefaultUser() = User(
        id = null,
        email = "",
        apiKey = "",
        name = "",
        company = null,  // Changed from companyName to company
        companyAlias = "",
        phone = null,
        credits = 0,
        isActive = false
    )
}