package com.gscube.smsbulker.repository

import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    fun getAllTemplates(): Flow<List<MessageTemplate>>
    suspend fun insertTemplate(template: MessageTemplate)
    suspend fun updateTemplate(template: MessageTemplate)
    suspend fun deleteTemplate(template: MessageTemplate)
    fun getTemplatesByCategory(category: TemplateCategory): Flow<List<MessageTemplate>>
    suspend fun syncTemplates(category: TemplateCategory)
    suspend fun addTemplate(template: MessageTemplate)
    suspend fun getTemplate(templateId: Long): MessageTemplate?
    suspend fun searchTemplates(query: String): List<MessageTemplate>
    suspend fun getAllCategories(): List<TemplateCategory>
    suspend fun clearAllTemplates()
    suspend fun importTemplates(templates: List<MessageTemplate>)
    suspend fun exportTemplates(): List<MessageTemplate>
}