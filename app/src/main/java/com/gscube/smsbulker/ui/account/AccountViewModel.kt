package com.gscube.smsbulker.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.CreditBalance
import com.gscube.smsbulker.data.Subscription
import com.gscube.smsbulker.data.UserProfile
import com.gscube.smsbulker.repository.AccountRepository
import com.gscube.smsbulker.repository.AuthRepository
import com.gscube.smsbulker.repository.FirebaseRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
    private val firebaseRepository: FirebaseRepository
) : ViewModel() {
    
    private val _userProfile = MutableStateFlow<Result<UserProfile>?>(null)
    val userProfile: StateFlow<Result<UserProfile>?> = _userProfile
    
    private val _creditBalance = MutableStateFlow<Result<CreditBalance>?>(null)
    val creditBalance: StateFlow<Result<CreditBalance>?> = _creditBalance
    
    private val _subscription = MutableStateFlow<Result<Subscription>?>(null)
    val subscription: StateFlow<Result<Subscription>?> = _subscription
    
    fun loadUserProfile() {
        viewModelScope.launch {
            try {
                firebaseRepository.getCurrentUser().onSuccess { profile ->
                    _userProfile.value = Result.success(profile!!)
                    // Assuming credit balance and subscription data are stored in the UserProfile
                    // or can be derived from it
                    profile.creditBalance?.let {
                        _creditBalance.value = Result.success(it)
                    }
                    profile.subscription?.let {
                        _subscription.value = Result.success(it)
                    }
                }.onFailure { error ->
                    _userProfile.value = Result.failure(error)
                }
            } catch (e: Exception) {
                _userProfile.value = Result.failure(e)
            }
        }
    }
    
    fun loadCreditBalance() {
        viewModelScope.launch {
            try {
                firebaseRepository.getCreditBalance().onSuccess { balance ->
                    _creditBalance.value = Result.success(balance)
                }.onFailure { error ->
                    _creditBalance.value = Result.failure(error)
                }
            } catch (e: Exception) {
                _creditBalance.value = Result.failure(e)
            }
        }
    }
    
    fun loadSubscription() {
        viewModelScope.launch {
            try {
                firebaseRepository.getSubscription().onSuccess { subscription ->
                    _subscription.value = Result.success(subscription)
                }.onFailure { error ->
                    _subscription.value = Result.failure(error)
                }
            } catch (e: Exception) {
                _subscription.value = Result.failure(e)
            }
        }
    }
    
    fun refreshApiKey() {
        viewModelScope.launch {
            firebaseRepository.refreshApiKey().onSuccess {
                loadUserProfile() // Reload profile to get new API key
            }
        }
    }
    
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}