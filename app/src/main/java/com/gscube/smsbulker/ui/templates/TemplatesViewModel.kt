package com.gscube.smsbulker.ui.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.TemplateRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class TemplatesViewModel @Inject constructor(
    private val templateRepository: TemplateRepository
) : ViewModel() {
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _selectedCategory = MutableLiveData<TemplateCategory>(TemplateCategory.GENERAL)
    val selectedCategory: LiveData<TemplateCategory> = _selectedCategory

    private val _templates = MutableLiveData<List<MessageTemplate>>()
    val templates: LiveData<List<MessageTemplate>> = _templates

    init {
        loadTemplates(TemplateCategory.GENERAL)
    }

    fun loadTemplates(category: TemplateCategory) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _selectedCategory.value = category
                templateRepository.getTemplatesByCategory(category).collectLatest { templates ->
                    _templates.value = templates
                }
            } catch (e: Exception) {
                _error.value = "Failed to load templates: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveTemplate(template: MessageTemplate) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                templateRepository.addTemplate(template)
                // Reload templates after saving
                loadTemplates(selectedCategory.value ?: TemplateCategory.GENERAL)
            } catch (e: Exception) {
                _error.value = "Failed to save template: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getCategories(): List<TemplateCategory> {
        return TemplateCategory.values().toList()
    }

    fun clearError() {
        _error.value = null
    }

    fun deleteTemplate(template: MessageTemplate) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                templateRepository.deleteTemplate(template)
                // Reload templates after deleting
                loadTemplates(selectedCategory.value ?: TemplateCategory.GENERAL)
            } catch (e: Exception) {
                _error.value = "Failed to delete template: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}