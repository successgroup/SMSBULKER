package com.gscube.smsbulker.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.AITemplateRepository
import com.gscube.smsbulker.repository.TemplateRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class AITemplateUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val generatedTemplate: MessageTemplate? = null,
    val prompt: String = "",
    val selectedCategory: TemplateCategory? = null
)

@Singleton
@ContributesMultibinding(AppScope::class)
class AITemplateViewModel @Inject constructor(
    private val aiTemplateRepository: AITemplateRepository,
    private val templateRepository: TemplateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AITemplateUiState())
    val uiState: StateFlow<AITemplateUiState> = _uiState.asStateFlow()

    fun setPrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun setCategory(category: TemplateCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun generateTemplate() {
        val currentState = _uiState.value
        if (currentState.prompt.isBlank()) {
            setError("Please enter a prompt for template generation")
            return
        }

        viewModelScope.launch {
            try {
                setLoading(true)
                clearError()
                
                val template = aiTemplateRepository.generateTemplate(
                    prompt = currentState.prompt,
                    category = currentState.selectedCategory
                )
                
                _uiState.update { it.copy(generatedTemplate = template) }
            } catch (e: Exception) {
                setError("Failed to generate template: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    fun improveTemplate(template: MessageTemplate, additionalPrompt: String? = null) {
        viewModelScope.launch {
            try {
                setLoading(true)
                clearError()
                
                val improvedTemplate = aiTemplateRepository.improveTemplate(
                    template = template,
                    prompt = additionalPrompt
                )
                
                _uiState.update { it.copy(generatedTemplate = improvedTemplate) }
            } catch (e: Exception) {
                setError("Failed to improve template: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    fun saveGeneratedTemplate() {
        val template = _uiState.value.generatedTemplate ?: return
        
        viewModelScope.launch {
            try {
                setLoading(true)
                templateRepository.insertTemplate(template)
                // Clear the generated template after saving
                _uiState.update { it.copy(generatedTemplate = null) }
            } catch (e: Exception) {
                setError("Failed to save template: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    fun discardGeneratedTemplate() {
        _uiState.update { it.copy(generatedTemplate = null) }
    }

    private fun setLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    private fun setError(error: String?) {
        _uiState.update { it.copy(error = error) }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}