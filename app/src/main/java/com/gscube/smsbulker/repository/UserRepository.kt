package com.gscube.smsbulker.repository

import com.gscube.smsbulker.data.model.User

interface UserRepository {
    suspend fun getCurrentUser(): User
    suspend fun updateUser(user: User)
} 