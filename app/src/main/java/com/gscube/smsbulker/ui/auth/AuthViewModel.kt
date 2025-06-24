package com.gscube.smsbulker.ui.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.gscube.smsbulker.repository.UserRepository
import com.gscube.smsbulker.repository.FirebaseRepository
import com.gscube.smsbulker.utils.SecureStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Named

class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val firebaseRepository: FirebaseRepository,
    @Named("applicationContext") private val context: Context,
    private val secureStorage: SecureStorage
) : ViewModel() {
    private val TAG = "AuthViewModel"
    private val auth = FirebaseAuth.getInstance()

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _authSuccess = MutableLiveData<Boolean>()
    val authSuccess: LiveData<Boolean> = _authSuccess

    private val _passwordResetSent = MutableLiveData<Boolean>()
    val passwordResetSent: LiveData<Boolean> = _passwordResetSent

    init {
        Log.d(TAG, "Initializing AuthViewModel")
        _authSuccess.value = false
        checkInitialLoginState()
    }

    private fun checkInitialLoginState() {
        auth.currentUser?.let { user ->
            Log.d(TAG, "Found Firebase user: ${user.uid}")
            if (!secureStorage.isLoggedIn()) {
                Log.d(TAG, "No local data found, saving auth data")
                secureStorage.saveAuthData(user.uid, "ZnhoSWFRbWhBWmpIc3N3eUNEZW8", user.email ?: "")
            }
        } ?: run {
            Log.d(TAG, "No Firebase user found")
            secureStorage.clearAuthData()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun login(email: String, password: String) {
        Log.d(TAG, "Attempting login for email: $email")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                _authSuccess.value = false
                
                // Check network connectivity first
                if (!isNetworkAvailable()) {
                    _error.postValue("No internet connection. Please check your network settings.")
                    return@launch
                }

                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user?.let { user ->
                    try {
                        Log.d(TAG, "Login successful for user: ${user.uid}")
                        secureStorage.saveAuthData(user.uid, "ZnhoSWFRbWhBWmpIc3N3eUNEZW8", email)
                        _authSuccess.postValue(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save auth data: ${e.message}")
                        _error.postValue("Failed to save authentication data")
                        auth.signOut()
                        secureStorage.clearAuthData()
                    }
                } ?: run {
                    Log.e(TAG, "Login successful but no user returned")
                    _error.postValue("Login failed - no user data")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login failed: ${e.message}")
                val errorMessage = when {
                    e.message?.contains("network error", ignoreCase = true) == true -> 
                        "Network error. Please check your internet connection and try again."
                    e.message?.contains("timeout", ignoreCase = true) == true -> 
                        "Connection timeout. Please try again."
                    e.message?.contains("interrupted connection", ignoreCase = true) == true -> 
                        "Connection interrupted. Please try again."
                    e.message?.contains("unreachable host", ignoreCase = true) == true -> 
                        "Server unreachable. Please try again later."
                    e.message?.contains("wrong password", ignoreCase = true) == true ->
                        "Incorrect password. Please try again."
                    e.message?.contains("no user record", ignoreCase = true) == true ->
                        "No account found with this email. Please sign up first."
                    e.message?.contains("invalid email", ignoreCase = true) == true ->
                        "Invalid email format. Please check and try again."
                    else -> "Authentication failed: ${e.message}"
                }
                _error.postValue(errorMessage)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun signup(email: String, password: String, companyName: String, phone: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                _authSuccess.value = false
                
                // Use FirebaseRepository to create user and Firestore document
                firebaseRepository.signUpWithEmailAndPassword(
                    email = email,
                    password = password,
                    name = companyName,
                    phone = phone,
                    company = companyName
                ).onSuccess { userProfile ->
                    try {
                        secureStorage.saveAuthData(userProfile.userId, "ZnhoSWFRbWhBWmpIc3N3eUNEZW8", email)
                        _authSuccess.value = true
                    } catch (e: Exception) {
                        _error.value = "Failed to save user data"
                        auth.signOut()
                        secureStorage.clearAuthData()
                    }
                }.onFailure { exception ->
                    _error.value = exception.message ?: "Signup failed"
                    auth.signOut()
                    secureStorage.clearAuthData()
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Signup failed"
                auth.signOut()
                secureStorage.clearAuthData()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                _passwordResetSent.value = false

                auth.sendPasswordResetEmail(email).await()
                _passwordResetSent.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to send password reset email"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendPasswordResetEmail(email: String) {
        resetPassword(email)
    }

    fun logout() {
        Log.d(TAG, "Logging out")
        auth.signOut()
        secureStorage.clearAuthData()
        _authSuccess.value = false
    }

    fun isLoggedIn(): Boolean {
        val firebaseUser = auth.currentUser
        val isLoggedIn = firebaseUser != null && secureStorage.isLoggedIn()
        Log.d(TAG, "Checking login state - Firebase: ${firebaseUser != null}, Local: ${secureStorage.isLoggedIn()}, Final: $isLoggedIn")
        return isLoggedIn
    }

    fun clearError() {
        _error.value = null
    }

    fun clearPasswordResetSent() {
        _passwordResetSent.value = false
    }
}