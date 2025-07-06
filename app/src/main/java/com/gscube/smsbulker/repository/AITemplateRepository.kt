package com.gscube.smsbulker.repository

import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory

interface AITemplateRepository {
    /**
     * Generates a message template based on a user prompt
     * @param prompt The user's prompt describing the template they want
     * @param category The category for the template (optional)
     * @return A generated MessageTemplate
     */
    suspend fun generateTemplate(prompt: String, category: TemplateCategory? = null): MessageTemplate
    
    /**
     * Suggests improvements for an existing template
     * @param template The existing template to improve
     * @param prompt Additional instructions for improvement (optional)
     * @return An improved MessageTemplate
     */
    suspend fun improveTemplate(template: MessageTemplate, prompt: String? = null): MessageTemplate
    
    /**
     * Extracts potential variables from a template content
     * @param content The template content to analyze
     * @return A list of variable names found in the content
     */
    suspend fun extractVariables(content: String): List<String>
}