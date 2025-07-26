package com.gscube.smsbulker.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class CreditPackage(
    val id: String,
    val name: String,
    val credits: Int,
    val price: Double,
    val currency: String = "GHS",
    val description: String,
    val bonusCredits: Int = 0,
    val isPopular: Boolean = false,
    val discountPercentage: Int = 0
) : Parcelable

@Parcelize
data class PaymentTransaction(
    val id: String,
    val userId: String,
    val packageId: String,
    val amount: Double,
    val currency: String,
    val credits: Int,
    val status: PaymentStatus,
    val paymentMethod: String = "paystack",
    val paymentChannel: String? = null, // Added for mobile money tracking
    val paystackReference: String,
    val createdAt: Timestamp,
    val completedAt: Timestamp? = null,
    val failureReason: String? = null
) : Parcelable

enum class PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED
}

@Parcelize
data class PaymentRequest(
    val packageId: String,
    val amount: Double,
    val currency: String,
    val email: String,
    val userId: String,
    val credits: Int,
    val mobileNumber: String? = null // Added for mobile money payments
) : Parcelable

@Parcelize
data class PaymentResponse(
    val success: Boolean,
    val transactionId: String?,
    val paystackReference: String?,
    val message: String,
    val credits: Int = 0
) : Parcelable

data class CreditCalculation(
    val selectedPackage: CreditPackage?,
    val customCredits: Int,
    val totalAmount: Double,
    val baseAmount: Double,
    val bonusCredits: Int,
    val totalCredits: Int,
    val pricePerCredit: Double
)