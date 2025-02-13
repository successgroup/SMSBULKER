package com.gscube.smsbulker.ui.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.CreditBalance
import com.gscube.smsbulker.data.Subscription
import com.gscube.smsbulker.data.UserProfile
import com.gscube.smsbulker.repository.AccountRepository
import com.gscube.smsbulker.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _userProfile = MutableStateFlow<Result<UserProfile>?>(null)
    val userProfile: StateFlow<Result<UserProfile>?> = _userProfile
    
    private val _creditBalance = MutableStateFlow<Result<CreditBalance>?>(null)
    val creditBalance: StateFlow<Result<CreditBalance>?> = _creditBalance
    
    private val _subscription = MutableStateFlow<Result<Subscription>?>(null)
    val subscription: StateFlow<Result<Subscription>?> = _subscription
    
    fun loadUserProfile() {
        viewModelScope.launch {
            _userProfile.value = accountRepository.getUserProfile()
        }
    }
    
    fun loadCreditBalance() {
        viewModelScope.launch {
            _creditBalance.value = accountRepository.getCreditBalance()
        }
    }
    
    fun loadSubscription() {
        viewModelScope.launch {
            _subscription.value = accountRepository.getSubscription()
        }
    }
    
    fun refreshApiKey() {
        viewModelScope.launch {
            accountRepository.refreshApiKey().onSuccess {
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