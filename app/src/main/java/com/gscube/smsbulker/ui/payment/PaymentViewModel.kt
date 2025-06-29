package com.gscube.smsbulker.ui.payment

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.*
import com.gscube.smsbulker.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class PaymentViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _packages = MutableStateFlow<List<CreditPackage>>(emptyList())
    val packages: StateFlow<List<CreditPackage>> = _packages.asStateFlow()

    private val _calculation = MutableStateFlow<CreditCalculation?>(null)
    val calculation: StateFlow<CreditCalculation?> = _calculation.asStateFlow()

    init {
        loadPackages()
    }

    fun loadPackages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            paymentRepository.getAvailablePackages()
                .onSuccess { packages ->
                    _packages.value = packages
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun selectPackage(creditPackage: CreditPackage) {
        val calculation = CreditCalculation(
            selectedPackage = creditPackage,
            customCredits = creditPackage.credits,
            totalAmount = creditPackage.price,
            baseAmount = creditPackage.price, // Use the package price directly as base amount
            bonusCredits = creditPackage.bonusCredits,
            totalCredits = creditPackage.credits + creditPackage.bonusCredits,
            pricePerCredit = creditPackage.price / creditPackage.credits
        )
        
        _calculation.value = calculation
        _uiState.value = _uiState.value.copy(
            selectedPackage = creditPackage,
            customCredits = null,
            error = null
        )
    }

    fun setCustomCredits(credits: String) {
        val creditsInt = credits.toIntOrNull()
        if (creditsInt != null && creditsInt > 0) {
            _uiState.value = _uiState.value.copy(
                customCredits = creditsInt,
                selectedPackage = null,
                calculationMode = CalculationMode.CREDITS_TO_PRICE
            )
            
            viewModelScope.launch {
                paymentRepository.calculateCustomCredits(creditsInt)
                    .onSuccess { calculation ->
                        _calculation.value = calculation
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(error = error.message)
                    }
            }
        } else {
            _calculation.value = null
            _uiState.value = _uiState.value.copy(customCredits = null)
        }
    }
    
    fun setCustomPrice(price: String) {
        val priceDouble = price.toDoubleOrNull()
        if (priceDouble != null && priceDouble > 0) {
            _uiState.value = _uiState.value.copy(
                customPrice = priceDouble,
                selectedPackage = null,
                calculationMode = CalculationMode.PRICE_TO_CREDITS
            )
            
            viewModelScope.launch {
                paymentRepository.calculateCreditsFromPrice(priceDouble)
                    .onSuccess { calculation ->
                        _calculation.value = calculation
                        _uiState.value = _uiState.value.copy(customCredits = calculation.customCredits)
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(error = error.message)
                    }
            }
        } else {
            _calculation.value = null
            _uiState.value = _uiState.value.copy(customPrice = null)
        }
    }

    fun initiatePayment(email: String, userId: String): LiveData<PaymentResponse> {
        val currentCalculation = _calculation.value ?: return MutableLiveData(null)
        val result = MutableLiveData<PaymentResponse>()
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingPayment = true)
            
            val request = PaymentRequest(
                packageId = currentCalculation.selectedPackage?.id ?: "custom",
                amount = currentCalculation.totalAmount,
                currency = "GHS",
                email = email,
                userId = userId
            )
            
            paymentRepository.initiatePayment(request)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingPayment = false,
                        paymentResponse = response
                    )
                    result.postValue(response)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isProcessingPayment = false,
                        error = error.message
                    )
                    result.postValue(PaymentResponse(
                        success = false,
                        transactionId = null,
                        paystackReference = null,
                        message = error.message ?: "Payment initialization failed"
                    ))
                }
        }
        
        return result
    }

    // Flag to prevent multiple verification attempts when rate limited
    private var isRateLimited = false
    
    fun verifyPayment(reference: String) {
        // Prevent multiple verification attempts if already rate limited
        if (isRateLimited) {
            Log.d("PaymentViewModel", "Skipping verification due to rate limiting")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isVerifyingPayment = true)
            
            paymentRepository.verifyPayment(reference)
                .onSuccess { transaction ->
                    if (transaction.status == PaymentStatus.SUCCESS) {
                        // Update user credits
                        paymentRepository.updateUserCredits(transaction.userId, transaction.credits)
                        
                        _uiState.value = _uiState.value.copy(
                            isVerifyingPayment = false,
                            paymentSuccess = true,
                            successMessage = "Payment successful! ${transaction.credits} credits added to your account."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isVerifyingPayment = false,
                            error = "Payment failed: ${transaction.failureReason}"
                        )
                    }
                }
                .onFailure { error ->
                    // Check if the error is related to rate limiting (429 Too Many Requests)
                    if (error.message?.contains("429") == true || 
                        error.message?.contains("Too many requests") == true) {
                        
                        isRateLimited = true
                        
                        // Set a more user-friendly error message
                        _uiState.value = _uiState.value.copy(
                            isVerifyingPayment = false,
                            error = "Server is busy. Please wait a moment before trying again."
                        )
                        
                        // Reset the rate limited flag after some time
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(15000) // 15 seconds delay
                            isRateLimited = false
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isVerifyingPayment = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearPaymentResponse() {
        _uiState.value = _uiState.value.copy(
            paymentResponse = null,
            paymentSuccess = false,
            successMessage = null
        )
    }
}

enum class CalculationMode {
    CREDITS_TO_PRICE,  // Calculate price based on credits
    PRICE_TO_CREDITS   // Calculate credits based on price
}

data class PaymentUiState(
    val isLoading: Boolean = false,
    val isProcessingPayment: Boolean = false,
    val isVerifyingPayment: Boolean = false,
    val selectedPackage: CreditPackage? = null,
    val customCredits: Int? = null,
    val customPrice: Double? = null,
    val calculationMode: CalculationMode = CalculationMode.CREDITS_TO_PRICE,
    val paymentResponse: PaymentResponse? = null,
    val paymentSuccess: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null
)