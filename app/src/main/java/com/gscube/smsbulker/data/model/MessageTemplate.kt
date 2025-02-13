package com.gscube.smsbulker.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "message_templates")
data class MessageTemplate(
    @PrimaryKey
    val id: String? = null,
    val title: String,
    val content: String,
    val category: TemplateCategory,
    val variables: List<String> = emptyList(),
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable
