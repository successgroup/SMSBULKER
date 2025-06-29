package com.gscube.smsbulker.repository.impl

import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.data.network.PaystackApiService
import com.gscube.smsbulker.repository.PaymentRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import com.gscube.smsbulker.di.AppScope
import com.squareup.anvil.annotations.ContributesBinding

@Singleton
@ContributesBinding(AppScope::class)
class PaymentRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val paystackApiService: PaystackApiService
) : PaymentRepository {

    companion object {
        private const val TRANSACTIONS_COLLECTION = "payment_transactions"
        private const val USERS_COLLECTION = "users"
        private const val BASE_PRICE_PER_CREDIT = 0.054
    }

    private val predefinedPackages = listOf(
        CreditPackage(
            id = "starter_20",
            name = "Starter Pack",
            credits = 350,
            price = 20.0,
            description = "Perfect for small businesses",
            bonusCredits = 35,
            discountPercentage = 10
        ),
        CreditPackage(
            id = "business_50",
            name = "Business Pack",
            credits = 875,
            price = 50.0,
            description = "Great for growing businesses",
            bonusCredits = 131,
            isPopular = true,
            discountPercentage = 15
        ),
        CreditPackage(
            id = "enterprise_100",
            name = "Enterprise Pack",
            credits = 1750,
            price = 100.0,
            description = "Best value for large operations",
            bonusCredits = 350,
            discountPercentage = 20
        ),
        CreditPackage(
            id = "premium_500",
            name = "Premium Pack",
            credits = 8750,
            price = 500.0,
            description = "Maximum savings for heavy users",
            bonusCredits = 2188,
            discountPercentage = 25
        )
    )

    override suspend fun getAvailablePackages(): Result<List<CreditPackage>> {
        return try {
            Result.success(predefinedPackages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun calculateCustomCredits(credits: Int): Result<CreditCalculation> {
        return try {
            val baseAmount = credits * BASE_PRICE_PER_CREDIT
            val bonusCredits = when {
                credits >= 2500 -> (credits * 0.25).toInt()
                credits >= 1000 -> (credits * 0.20).toInt()
                credits >= 500 -> (credits * 0.15).toInt()
                credits >= 100 -> (credits * 0.10).toInt()
                else -> 0
            }
            
            val discountPercentage = when {
                credits >= 2500 -> 25
                credits >= 1000 -> 20
                credits >= 500 -> 15
                credits >= 100 -> 10
                else -> 0
            }
            
            val totalAmount = baseAmount * (100 - discountPercentage) / 100
            val totalCredits = credits + bonusCredits
            val pricePerCredit = totalAmount / credits
            
            val calculation = CreditCalculation(
                selectedPackage = null,
                customCredits = credits,
                totalAmount = totalAmount,
                baseAmount = baseAmount,
                bonusCredits = bonusCredits,
                totalCredits = totalCredits,
                pricePerCredit = pricePerCredit
            )
            
            Result.success(calculation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun calculateCreditsFromPrice(price: Double): Result<CreditCalculation> {
        return try {
            // Calculate how many credits the price can buy at base rate
            val baseCredits = (price / BASE_PRICE_PER_CREDIT).toInt()
            
            // Determine bonus credits based on the credit tier
            val bonusCredits = when {
                baseCredits >= 2500 -> (baseCredits * 0.25).toInt()
                baseCredits >= 1000 -> (baseCredits * 0.20).toInt()
                baseCredits >= 500 -> (baseCredits * 0.15).toInt()
                baseCredits >= 100 -> (baseCredits * 0.10).toInt()
                else -> 0
            }
            
            // Calculate discount percentage based on the credit tier
            val discountPercentage = when {
                baseCredits >= 2500 -> 25
                baseCredits >= 1000 -> 20
                baseCredits >= 500 -> 15
                baseCredits >= 100 -> 10
                else -> 0
            }
            
            // Apply discount to the base amount
            val baseAmount = baseCredits * BASE_PRICE_PER_CREDIT
            val totalAmount = price // Use the exact price provided
            val totalCredits = baseCredits + bonusCredits
            val pricePerCredit = if (baseCredits > 0) totalAmount / baseCredits else 0.0
            
            val calculation = CreditCalculation(
                selectedPackage = null,
                customCredits = baseCredits,
                totalAmount = totalAmount,
                baseAmount = baseAmount,
                bonusCredits = bonusCredits,
                totalCredits = totalCredits,
                pricePerCredit = pricePerCredit
            )
            
            Result.success(calculation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun initiatePayment(request: PaymentRequest): Result<PaymentResponse> {
        return try {
            // Call our Firebase Cloud Function to initialize the Paystack transaction
            val response = paystackApiService.initializeTransaction(request)
            
            if (response.isSuccessful && response.body() != null) {
                val paymentResponse = response.body()!!
                
                // Check if we have a valid access_code from Paystack
                if (paymentResponse.success && !paymentResponse.paystackReference.isNullOrEmpty()) {
                    Result.success(paymentResponse)
                } else {
                    Result.failure(Exception(paymentResponse.message))
                }
            } else {
                Result.failure(Exception("Failed to initialize payment: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyPayment(reference: String): Result<PaymentTransaction> {
        return try {
            // Call our Firebase Cloud Function to verify the Paystack transaction
            val response = paystackApiService.verifyTransaction(reference)
            
            if (response.isSuccessful && response.body() != null) {
                val transaction = response.body()!!
                
                // If the transaction was successful, save it to Firestore
                if (transaction.status == PaymentStatus.SUCCESS) {
                    saveTransaction(transaction)
                }
                
                Result.success(transaction)
            } else {
                // Check for rate limiting (HTTP 429)
                if (response.code() == 429) {
                    val retryAfter = response.headers()["Retry-After"] ?: "60"
                    val errorMessage = "Too many requests. Please try again after $retryAfter seconds."
                    android.util.Log.e("PaymentRepository", errorMessage)
                    Result.failure(Exception(errorMessage))
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    android.util.Log.e("PaymentRepository", "Failed to verify payment: $errorBody")
                    Result.failure(Exception("Failed to verify payment: $errorBody"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PaymentRepository", "Exception during payment verification", e)
            Result.failure(e)
        }
    }

    override suspend fun getPaymentHistory(userId: String): Flow<List<PaymentTransaction>> = flow {
        try {
            val snapshot = firestore.collection(TRANSACTIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            val transactions = snapshot.documents.mapNotNull { doc ->
                doc.toObject(PaymentTransaction::class.java)?.copy(id = doc.id)
            }
            
            emit(transactions)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun updateUserCredits(userId: String, credits: Int): Result<Boolean> {
        return try {
            val userRef = firestore.collection(USERS_COLLECTION).document(userId)
            
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentCredits = snapshot.getLong("credits")?.toInt() ?: 0
                val newCredits = currentCredits + credits
                
                transaction.update(userRef, "credits", newCredits)
                transaction.update(userRef, "lastCreditUpdate", Timestamp.now())
            }.await()
            
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveTransaction(transaction: PaymentTransaction): Result<Boolean> {
        return try {
            firestore.collection(TRANSACTIONS_COLLECTION)
                .document(transaction.id)
                .set(transaction)
                .await()
            
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}