package com.gscube.smsbulker.ui.templates.ai

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory
import com.gscube.smsbulker.repository.AITemplateRepository
import com.gscube.smsbulker.repository.TemplateRepository
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

class AITemplateViewModel @Inject constructor(
    private val aiTemplateRepository: AITemplateRepository,
    private val templateRepository: TemplateRepository
) : ViewModel() {

    // UI State
    data class UIState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val template: MessageTemplate? = null
    )

    private val _uiState = MutableLiveData(UIState())
    val uiState: LiveData<UIState> = _uiState

    // Generated template
    private val _generatedTemplate = MutableLiveData<MessageTemplate?>()
    val generatedTemplate: LiveData<MessageTemplate?> = _generatedTemplate

    // Prompt and category
    private var prompt: String = ""
    private var category: TemplateCategory? = null

    fun setPrompt(prompt: String) {
        this.prompt = prompt
    }

    fun setCategory(category: TemplateCategory?) {
        this.category = category
    }

    fun generateTemplate() {
        if (prompt.isBlank()) {
            _uiState.value = UIState(error = "Please enter a prompt")
            return
        }

        _uiState.value = UIState(isLoading = true)

        viewModelScope.launch {
            try {
                val template = aiTemplateRepository.generateTemplate(prompt, category)
                _generatedTemplate.value = template
                _uiState.value = UIState(template = template)
            } catch (e: Exception) {
                _uiState.value = UIState(error = "Failed to generate template: ${e.message}")
            }
        }
    }

    fun improveTemplate() {
        val currentTemplate = _generatedTemplate.value ?: return

        _uiState.value = UIState(isLoading = true)

        viewModelScope.launch {
            try {
                val improvedTemplate = aiTemplateRepository.improveTemplate(currentTemplate, prompt)
                _generatedTemplate.value = improvedTemplate
                _uiState.value = UIState(template = improvedTemplate)
            } catch (e: Exception) {
                _uiState.value = UIState(error = "Failed to improve template: ${e.message}")
            }
        }
    }

    fun saveTemplate() {
        val template = _generatedTemplate.value ?: return

        viewModelScope.launch {
            try {
                // Ensure the template has an ID
                val templateToSave = if (template.id == null) {
                    template.copy(id = UUID.randomUUID().toString())
                } else {
                    template
                }

                templateRepository.insertTemplate(templateToSave)
            } catch (e: Exception) {
                _uiState.value = UIState(
                    template = _generatedTemplate.value,
                    error = "Failed to save template: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value?.copy(error = null)
    }
    
    fun discardTemplate() {
        _generatedTemplate.value = null
        _uiState.value = UIState()
    }
}