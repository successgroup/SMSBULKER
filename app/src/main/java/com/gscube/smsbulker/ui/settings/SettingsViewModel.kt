package com.gscube.smsbulker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.User
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.UserRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class SettingsViewState(
    val isLoading: Boolean = false,
    val apiKey: String? = null,
    val message: String? = null
)

@ContributesMultibinding(AppScope::class)
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsViewState())
    val state: StateFlow<SettingsViewState> = _state.asStateFlow()

    init {
        loadApiKey()
    }

    private fun loadApiKey() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                val user = userRepository.getCurrentUser()
                _state.update { it.copy(
                    apiKey = user.apiKey,
                    isLoading = false
                )}
            } catch (e: Exception) {
                _state.update { it.copy(
                    message = "Failed to load API key: ${e.message}",
                    isLoading = false
                )}
            }
        }
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                val currentUser = userRepository.getCurrentUser()
                val updatedUser = currentUser.copy(apiKey = apiKey)
                userRepository.updateUser(updatedUser)
                _state.update { it.copy(
                    apiKey = apiKey,
                    message = "API key saved successfully",
                    isLoading = false
                )}
            } catch (e: Exception) {
                _state.update { it.copy(
                    message = "Failed to save API key: ${e.message}",
                    isLoading = false
                )}
            }
        }
    }
}
