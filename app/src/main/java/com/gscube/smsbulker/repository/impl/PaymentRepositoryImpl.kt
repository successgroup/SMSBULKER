package com.gscube.smsbulker.repository.impl

import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.repository.PaymentRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : PaymentRepository {

    companion object {
        private const val TRANSACTIONS_COLLECTION = "payment_transactions"
        private const val USERS_COLLECTION = "users"
        private const val BASE_PRICE_PER_CREDIT = 0.057 // 2 NGN per credit
    }

    private val predefinedPackages = listOf(
        CreditPackage(
            id = "starter_100",
            name = "Starter Pack",
            credits = 100,
            price = 180.0,
            description = "Perfect for small businesses",
            bonusCredits = 10,
            discountPercentage = 10
        ),
        CreditPackage(
            id = "business_500",
            name = "Business Pack",
            credits = 500,
            price = 850.0,
            description = "Great for growing businesses",
            bonusCredits = 75,
            isPopular = true,
            discountPercentage = 15
        ),
        CreditPackage(
            id = "enterprise_1000",
            name = "Enterprise Pack",
            credits = 1000,
            price = 1600.0,
            description = "Best value for large operations",
            bonusCredits = 200,
            discountPercentage = 20
        ),
        CreditPackage(
            id = "premium_2500",
            name = "Premium Pack",
            credits = 2500,
            price = 3750.0,
            description = "Maximum savings for heavy users",
            bonusCredits = 625,
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

    override suspend fun initiatePayment(request: PaymentRequest): Result<PaymentResponse> {
        return try {
            // In a real implementation, this would call Paystack API
            // For now, we'll create a mock response
            val reference = "ps_${System.currentTimeMillis()}"
            
            val response = PaymentResponse(
                success = true,
                transactionId = "txn_${System.currentTimeMillis()}",
                paystackReference = reference,
                message = "Payment initiated successfully"
            )
            
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyPayment(reference: String): Result<PaymentTransaction> {
        return try {
            // In a real implementation, this would verify with Paystack API
            // For now, we'll create a mock successful transaction
            val transaction = PaymentTransaction(
                id = "txn_${System.currentTimeMillis()}",
                userId = "current_user", // This should come from auth
                packageId = "custom",
                amount = 1000.0,
                currency = "NGN",
                credits = 500,
                status = PaymentStatus.SUCCESS,
                paystackReference = reference,
                createdAt = Timestamp.now(),
                completedAt = Timestamp.now()
            )
            
            Result.success(transaction)
        } catch (e: Exception) {
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