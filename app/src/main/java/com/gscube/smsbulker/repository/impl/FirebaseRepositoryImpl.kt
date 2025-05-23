package com.gscube.smsbulker.repository.impl

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.gscube.smsbulker.data.CreditBalance
import com.gscube.smsbulker.data.Subscription
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
import com.gscube.smsbulker.utils.NetworkUtils
import com.google.firebase.firestore.FieldValue

@Singleton
@ContributesBinding(AppScope::class)
class FirebaseRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val secureStorage: SecureStorage,
    private val networkUtils: NetworkUtils
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
                        createdAt = (userDoc.getTimestamp("createdAt") ?.seconds ?: 0) * 1000L,
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
                // Generate a unique proxy API key for this user
                val proxyApiKey = UUID.randomUUID().toString()
                
                val profile = UserProfile(
                    userId = firebaseUser.uid,
                    email = email,
                    name = name,
                    phone = phone,
                    company = company,
                    emailVerified = false,
                    apiKey = proxyApiKey,  // This is the proxy key, not the actual Arkesel key
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
                // Get user document
                val userDoc = firestore.collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await()
                
                // Inside getCurrentUser() method where userDoc.exists() is true
                if (userDoc.exists()) {
                    // Declare creditBalanceDoc outside the try-catch
                    var creditBalanceDoc: com.google.firebase.firestore.DocumentSnapshot? = null
                    
                    // Get credit balance
                    try {
                        // Try server first
                        creditBalanceDoc = firestore.collection("users")
                            .document(currentUser.uid)
                            .collection("creditBalance")
                            .document("current")
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .await()
                        
                        // Process document...
                    } catch (e: Exception) {
                        // If server fails, try cache
                        android.util.Log.d("FirebaseRepo", "Server fetch failed, using cache: ${e.message}")
                        creditBalanceDoc = firestore.collection("users")
                            .document(currentUser.uid)
                            .collection("creditBalance")
                            .document("current")
                            .get(com.google.firebase.firestore.Source.CACHE)
                            .await()
                        
                        // Process document...
                    }
                    
                    // Get subscription
                    val subscriptionDoc = firestore.collection("users")
                        .document(currentUser.uid)
                        .collection("subscription")
                        .document("current")
                        .get() // Remove the source parameter here
                        .await()
                    
                    // Create credit balance object if document exists
                    val creditBalance = if (creditBalanceDoc?.exists() == true) {
                        CreditBalance(
                            availableCredits = creditBalanceDoc?.getDouble("availableCredits") ?: 0.0,
                            usedCredits = creditBalanceDoc?.getDouble("usedCredits") ?: 0.0,
                            lastUpdated = (creditBalanceDoc?.getTimestamp("lastUpdated")?.seconds ?: 0) * 1000L,
                            nextRefillDate = (creditBalanceDoc?.getTimestamp("nextRefillDate")?.seconds ?: 0) * 1000L,
                            autoRefillEnabled = creditBalanceDoc?.getBoolean("autoRefillEnabled") ?: false,
                            lowBalanceAlert = creditBalanceDoc?.getDouble("lowBalanceAlert") ?: 100.0
                        )
                    } else null
                    
                    // Create subscription object if document exists
                    val subscription = if (subscriptionDoc.exists()) {
                        Subscription(
                            planId = subscriptionDoc.getString("planId") ?: "free",
                            planName = subscriptionDoc.getString("planName") ?: "Free",
                            status = subscriptionDoc.getString("status") ?: "active",
                            startDate = (subscriptionDoc.getTimestamp("startDate") ?.seconds ?: 0) * 1000L,
                            // Original line with error:
                            endDate = (subscriptionDoc.getTimestamp("endDate")?.seconds ?: 0) * 1000L,
                            autoRenew = subscriptionDoc.getBoolean("autoRenew") ?: false,
                            monthlyCredits = subscriptionDoc.getDouble("monthlyCredits") ?: 100.0,
                            price = subscriptionDoc.getDouble("price") ?: 0.0,
                            features = (subscriptionDoc.get("features") as? List<String>) ?: listOf("Basic SMS")
                        )
                    } else null
                    
                    // Create user profile with credit balance and subscription
                    val profile = UserProfile(
                        userId = currentUser.uid,
                        email = currentUser.email ?: "",
                        name = userDoc.getString("name") ?: "",
                        phone = userDoc.getString("phone") ?: "",
                        company = userDoc.getString("company"),
                        companyAlias = userDoc.getString("companyAlias") ?: "",
                        emailVerified = currentUser.isEmailVerified,
                        apiKey = userDoc.getString("apiKey") ?: "",
                        createdAt = (userDoc.getTimestamp("createdAt") ?.seconds ?: 0) * 1000L,
                        lastLogin = (userDoc.getTimestamp("lastLogin") ?.seconds ?: 0) * 1000L,
                        creditBalance = creditBalance,
                        subscription = subscription
                    )
                    
                    Result.success(profile)
                } else {
                    Result.success(null)
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

    // Change this method in FirebaseRepositoryImpl
    override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))
            
            // Update main user document
            val userUpdates = hashMapOf<String, Any>(
                "name" to profile.name,
                "phone" to profile.phone,
                "company" to (profile.company ?: ""),
                "companyAlias" to profile.companyAlias,
                "lastLogin" to System.currentTimeMillis()
            )
            
            firestore.collection("users")
                .document(currentUser.uid)
                .update(userUpdates)
                .await()
            
            // Update credit balance if provided
            profile.creditBalance?.let { creditBalance ->
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("creditBalance")
                    .document("current")
                    .set(creditBalance)
                    .await()
            }
            
            // Update subscription if provided
            profile.subscription?.let { subscription ->
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("subscription")
                    .document("current")
                    .set(subscription)
                    .await()
            }
            
            Result.success(Unit)  // Return Unit instead of profile
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

    override suspend fun refreshApiKey(): Result<Unit> {
        return try {
            auth.currentUser?.let { firebaseUser ->
                val newApiKey = UUID.randomUUID().toString()
                firestore.collection("users")
                    .document(firebaseUser.uid)
                    .update("apiKey", newApiKey)
                    .await()
                Result.success(Unit)
            } ?: Result.failure(Exception("No user logged in"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCreditBalance(): Result<CreditBalance> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))
            
            // Log the path we're querying for debugging
            val path = "users/${currentUser.uid}/creditBalance/current"
            android.util.Log.d("FirebaseRepo", "Querying credit balance at path: $path")
            
            // Check network connectivity
            val source = if (networkUtils.isNetworkAvailable()) {
                com.google.firebase.firestore.Source.SERVER
            } else {
                android.util.Log.d("FirebaseRepo", "Network unavailable, using cached data for credit balance")
                com.google.firebase.firestore.Source.CACHE
            }
            
            val creditBalanceDoc = firestore.collection("users")
                .document(currentUser.uid)
                .collection("creditBalance")
                .document("current")
                .get() // Remove the source parameter to use default behavior
                .await()
            
            if (creditBalanceDoc.exists()) {
                android.util.Log.d("FirebaseRepo", "Credit balance document exists")
                val creditBalance = CreditBalance(
                    availableCredits = creditBalanceDoc.getDouble("availableCredits") ?: 0.0,
                    usedCredits = creditBalanceDoc.getDouble("usedCredits") ?: 0.0,
                    lastUpdated = (creditBalanceDoc.getTimestamp("lastUpdated")?.seconds ?: 0) * 1000L,
                    nextRefillDate = creditBalanceDoc.getTimestamp("nextRefillDate")?.seconds?.let { it * 1000L },
                    autoRefillEnabled = creditBalanceDoc.getBoolean("autoRefillEnabled") ?: false,
                    lowBalanceAlert = creditBalanceDoc.getDouble("lowBalanceAlert") ?: 100.0
                )
                Result.success(creditBalance)
            } else {
                android.util.Log.d("FirebaseRepo", "Credit balance document does not exist")
                // Return default credit balance if document doesn't exist
                Result.success(CreditBalance(
                    availableCredits = 0.0,
                    usedCredits = 0.0,
                    lastUpdated = System.currentTimeMillis(),
                    nextRefillDate = null,
                    autoRefillEnabled = false,
                    lowBalanceAlert = 100.0
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepo", "Error getting credit balance: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getSubscription(): Result<Subscription> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))
            
            // Log the path we're querying for debugging
            val path = "users/${currentUser.uid}/subscription/current"
            android.util.Log.d("FirebaseRepo", "Querying subscription at path: $path")
            
            // Check network connectivity
            val source = if (networkUtils.isNetworkAvailable()) {
                com.google.firebase.firestore.Source.SERVER
            } else {
                android.util.Log.d("FirebaseRepo", "Network unavailable, using cached data for subscription")
                com.google.firebase.firestore.Source.CACHE
            }
            
            val subscriptionDoc = firestore.collection("users")
                .document(currentUser.uid)
                .collection("subscription")
                .document("current")
                .get() // Remove the source parameter to use default behavior
                .await()
            
            if (subscriptionDoc.exists()) {
                android.util.Log.d("FirebaseRepo", "Subscription document exists")
                val subscription = Subscription(
                    planId = subscriptionDoc.getString("planId") ?: "",
                    planName = subscriptionDoc.getString("planName") ?: "",
                    status = subscriptionDoc.getString("status") ?: "expired",
                    startDate = (subscriptionDoc.getTimestamp("startDate")?.seconds ?: 0) * 1000L,
                    endDate = (subscriptionDoc.getTimestamp("endDate")?.seconds ?: 0) * 1000L,
                    autoRenew = subscriptionDoc.getBoolean("autoRenew") ?: false,
                    monthlyCredits = subscriptionDoc.getDouble("monthlyCredits") ?: 0.0,
                    price = subscriptionDoc.getDouble("price") ?: 0.0,
                    features = subscriptionDoc.get("features") as? List<String> ?: emptyList()
                )
                Result.success(subscription)
            } else {
                android.util.Log.d("FirebaseRepo", "Subscription document does not exist")
                // Return default subscription if document doesn't exist
                Result.success(Subscription(
                    planId = "free",
                    planName = "Free",
                    status = "active",
                    startDate = System.currentTimeMillis(),
                    endDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000,
                    autoRenew = false,
                    monthlyCredits = 100.0,
                    price = 0.0,
                    features = listOf("Basic SMS", "Contact Management")
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepo", "Error getting subscription: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun deductCredits(messageCount: Int): Result<Double> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not authenticated"))
            val userRef = firestore.collection("users").document(currentUser.uid)
            
            val result = firestore.runTransaction { transaction ->
                val creditBalanceDoc = transaction.get(userRef.collection("creditBalance").document("current"))
                
                val availableCredits = creditBalanceDoc.getDouble("availableCredits") ?: 0.0
                val usedCredits = creditBalanceDoc.getDouble("usedCredits") ?: 0.0
                
                if (availableCredits < messageCount) {
                    throw Exception("Insufficient credits: Available $availableCredits, Required $messageCount")
                }
                
                val newAvailableCredits = availableCredits - messageCount
                val newUsedCredits = usedCredits + messageCount
                
                transaction.update(creditBalanceDoc.reference, mapOf(
                    "availableCredits" to newAvailableCredits,
                    "usedCredits" to newUsedCredits,
                    "lastUpdated" to FieldValue.serverTimestamp()
                ))
                
                newAvailableCredits
            }.await()
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
