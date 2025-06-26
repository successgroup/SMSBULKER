package com.gscube.smsbulker.repository

import com.gscube.smsbulker.data.model.*
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    suspend fun getAvailablePackages(): Result<List<CreditPackage>>
    suspend fun calculateCustomCredits(credits: Int): Result<CreditCalculation>
    suspend fun calculateCreditsFromPrice(price: Double): Result<CreditCalculation>
    suspend fun initiatePayment(request: PaymentRequest): Result<PaymentResponse>
    suspend fun verifyPayment(reference: String): Result<PaymentTransaction>
    suspend fun getPaymentHistory(userId: String): Flow<List<PaymentTransaction>>
    suspend fun updateUserCredits(userId: String, credits: Int): Result<Boolean>
    suspend fun saveTransaction(transaction: PaymentTransaction): Result<Boolean>
}