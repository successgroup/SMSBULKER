package com.gscube.smsbulker.repository.impl

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.gscube.smsbulker.data.UserProfile
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.FirebaseRepository
import com.gscube.smsbulker.utils.SecureStorage
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import java.util.*

@Singleton
@ContributesBinding(AppScope::class)
class FirebaseRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val secureStorage: SecureStorage
) : FirebaseRepository {

    override suspend fun signInWithEmailAndPassword(email: String, password: String): Result<UserProfile> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.let { firebaseUser ->
                // Get additional user data from Firestore
                val userDoc = firestore.collection("users")
                    .document(firebaseUser.uid)
                    .get()
                    .await()
                
                if (userDoc.exists()) {
                    val profile = UserProfile(
                        userId = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        name = userDoc.getString("name") ?: "",
                        phone = userDoc.getString("phone") ?: "",
                        company = userDoc.getString("company"),
                        emailVerified = firebaseUser.isEmailVerified,
                        apiKey = userDoc.getString("apiKey") ?: "",
                        createdAt = userDoc.getLong("createdAt") ?: System.currentTimeMillis(),
                        lastLogin = System.currentTimeMillis()
                    )
                    Result.success(profile)
                } else {
                    Result.failure(Exception("User profile not found"))
                }
            } ?: Result.failure(Exception("Authentication failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmailAndPassword(
        email: String,
        password: String,
        name: String,
        phone: String,
        company: String?
    ): Result<UserProfile> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { firebaseUser ->
                val profile = UserProfile(
                    userId = firebaseUser.uid,
                    email = email,
                    name = name,
                    phone = phone,
                    company = company,
                    emailVerified = false,
                    apiKey = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis(),
                    lastLogin = System.currentTimeMillis()
                )
                
                // Save additional user data to Firestore
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .set(profile)
                    .await()
                
                Result.success(profile)
            } ?: Result.failure(Exception("User creation failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUser(): Result<UserProfile?> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Result.success(null)
            } else {
                try {
                    val userDoc = firestore.collection("users")
                        .document(currentUser.uid)
                        .get()
                        .await()
                    
                    if (userDoc.exists()) {
                        val profile = UserProfile(
                            userId = currentUser.uid,
                            email = currentUser.email ?: "",
                            name = userDoc.getString("name") ?: "",
                            phone = userDoc.getString("phone") ?: "",
                            company = userDoc.getString("company"),
                            emailVerified = currentUser.isEmailVerified,
                            apiKey = userDoc.getString("apiKey") ?: "",
                            createdAt = userDoc.getLong("createdAt") ?: System.currentTimeMillis(),
                            lastLogin = System.currentTimeMillis()
                        )

                        // Store in SecureStorage for offline access
                        secureStorage.saveUserProfile(profile)
                        
                        Result.success(profile)
                    } else {
                        // Try to get from SecureStorage if Firestore fails
                        val savedProfile = secureStorage.getUserProfile()
                        if (savedProfile != null) {
                            Result.success(savedProfile)
                        } else {
                            Result.failure(Exception("User profile not found"))
                        }
                    }
                } catch (e: Exception) {
                    // If Firestore fails, try SecureStorage
                    val savedProfile = secureStorage.getUserProfile()
                    if (savedProfile != null) {
                        Result.success(savedProfile)
                    } else {
                        Result.failure(e)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        secureStorage.clearAuthData()
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            auth.currentUser?.let { firebaseUser ->
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .set(profile)
                    .await()
                Result.success(Unit)
            } ?: Result.failure(Exception("No user logged in"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            auth.currentUser?.let { firebaseUser ->
                // Delete Firestore data
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .delete()
                    .await()
                
                // Delete Firebase Auth account
                firebaseUser.delete().await()
                
                // Clear local data
                secureStorage.clearAuthData()
                
                Result.success(Unit)
            } ?: Result.failure(Exception("No user logged in"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyEmail(): Result<Unit> {
        return try {
            auth.currentUser?.let { firebaseUser ->
                firebaseUser.sendEmailVerification().await()
                Result.success(Unit)
            } ?: Result.failure(Exception("No user logged in"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isEmailVerified(): Boolean {
        return auth.currentUser?.isEmailVerified ?: false
    }

    override suspend fun reloadUser(): Result<Unit> {
        return try {
            auth.currentUser?.reload()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
