package com.gscube.smsbulker.repository.impl

import android.content.Context
import com.gscube.smsbulker.data.local.TemplateDatabase
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.TemplateRepository
import com.gscube.smsbulker.utils.SampleTemplates
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@ContributesBinding(AppScope::class)
class TemplateRepositoryImpl @Inject constructor(
    @Named("applicationContext") private val context: Context,
    private val database: TemplateDatabase
) : TemplateRepository {

    override fun getAllTemplates(): Flow<List<MessageTemplate>> {
        return flow {
            withContext(Dispatchers.IO) {
                val cursor = database.readableDatabase.query(
                    "templates",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "created_at DESC"
                )
                
                cursor.use { c ->
                    val templates = mutableListOf<MessageTemplate>()
                    if (c.moveToFirst()) {
                        do {
                            val id = c.getString(c.getColumnIndexOrThrow("id"))
                            val title = c.getString(c.getColumnIndexOrThrow("title"))
                            val content = c.getString(c.getColumnIndexOrThrow("content"))
                            val categoryStr = c.getString(c.getColumnIndexOrThrow("category"))
                            val variablesJson = c.getString(c.getColumnIndexOrThrow("variables"))
                            val createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                            val isCustom = c.getInt(c.getColumnIndexOrThrow("is_custom")) == 1
                            
                            val category = try {
                                TemplateCategory.valueOf(categoryStr)
                            } catch (e: IllegalArgumentException) {
                                TemplateCategory.GENERAL
                            }
                            
                            val variables = database.getVariablesFromJson(variablesJson)
                            
                            templates.add(
                                MessageTemplate(
                                    id = id,
                                    title = title,
                                    content = content,
                                    category = category,
                                    variables = variables,
                                    createdAt = createdAt,
                                    isCustom = isCustom
                                )
                            )
                        } while (c.moveToNext())
                    }
                    emit(templates)
                }
            }
        }
    }

    override suspend fun insertTemplate(template: MessageTemplate) {
        withContext(Dispatchers.IO) {
            database.insertTemplate(template)
        }
    }

    override suspend fun updateTemplate(template: MessageTemplate) {
        withContext(Dispatchers.IO) {
            // Since SQLite doesn't have a direct update method, we'll delete and insert
            database.deleteTemplate(template)
            database.insertTemplate(template)
        }
    }

    override suspend fun deleteTemplate(template: MessageTemplate) {
        withContext(Dispatchers.IO) {
            database.deleteTemplate(template)
        }
    }

    override fun getTemplatesByCategory(category: TemplateCategory): Flow<List<MessageTemplate>> {
        return database.getTemplatesByCategory(category)
    }

    override suspend fun syncTemplates(category: TemplateCategory) {
        // In a real app, you would fetch templates from your backend API here
        // For now, we'll use sample templates
        val templates = when (category) {
            TemplateCategory.BUSINESS -> SampleTemplates.businessTemplates
            TemplateCategory.SCHOOL -> SampleTemplates.schoolTemplates
            TemplateCategory.MARKETTING -> SampleTemplates.marketingTemplates
            TemplateCategory.CHURCH -> SampleTemplates.churchTemplates
            TemplateCategory.BANK -> SampleTemplates.bankTemplates
            TemplateCategory.CLUB -> SampleTemplates.clubTemplates
            TemplateCategory.GENERAL -> SampleTemplates.generalTemplates
            TemplateCategory.ALERT -> SampleTemplates.alertTemplates
            TemplateCategory.REMINDERS -> SampleTemplates.reminderTemplates
            TemplateCategory.NOTIFICATIONS -> SampleTemplates.notificationTemplates
        }
        withContext(Dispatchers.IO) {
            database.insertTemplates(templates)
        }
    }

    override suspend fun addTemplate(template: MessageTemplate) {
        insertTemplate(template)
    }

    override suspend fun getTemplate(templateId: Long): MessageTemplate? {
        return withContext(Dispatchers.IO) {
            database.getTemplatesByCategory(TemplateCategory.values().first())
                .first()
                .find { it.id == templateId.toString() }
        }
    }

    override suspend fun searchTemplates(query: String): List<MessageTemplate> {
        return withContext(Dispatchers.IO) {
            val allTemplates = mutableListOf<MessageTemplate>()
            TemplateCategory.values().forEach { category ->
                database.getTemplatesByCategory(category)
                    .first()
                    .filter { 
                        it.title.contains(query, ignoreCase = true) || 
                        it.content.contains(query, ignoreCase = true) 
                    }
                    .let { allTemplates.addAll(it) }
            }
            allTemplates
        }
    }

    override suspend fun getAllCategories(): List<TemplateCategory> {
        return TemplateCategory.values().toList()
    }

    override suspend fun clearAllTemplates() {
        withContext(Dispatchers.IO) {
            TemplateCategory.values().forEach { category ->
                database.deleteNonCustomTemplatesByCategory(category)
            }
        }
    }

    override suspend fun importTemplates(templates: List<MessageTemplate>) {
        withContext(Dispatchers.IO) {
            database.insertTemplates(templates)
        }
    }

    override suspend fun exportTemplates(): List<MessageTemplate> {
        return withContext(Dispatchers.IO) {
            val allTemplates = mutableListOf<MessageTemplate>()
            TemplateCategory.values().forEach { category ->
                database.getTemplatesByCategory(category)
                    .first()
                    .let { allTemplates.addAll(it) }
            }
            allTemplates
        }
    }
}
