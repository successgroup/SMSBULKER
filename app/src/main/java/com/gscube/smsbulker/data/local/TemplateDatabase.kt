package com.gscube.smsbulker.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID

class TemplateDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "smsbulker.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_TEMPLATES = "templates"

        // Column names
        private const val COLUMN_ID = "id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_CONTENT = "content"
        private const val COLUMN_CATEGORY = "category"
        private const val COLUMN_VARIABLES = "variables"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val COLUMN_IS_CUSTOM = "is_custom"
        private const val COLUMN_LAST_SYNCED_AT = "last_synced_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_TEMPLATES (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_CATEGORY TEXT NOT NULL,
                $COLUMN_VARIABLES TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_IS_CUSTOM INTEGER NOT NULL,
                $COLUMN_LAST_SYNCED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TEMPLATES")
        onCreate(db)
    }

    private fun ContentValues.put(template: MessageTemplate) {
        put(COLUMN_ID, template.id ?: UUID.randomUUID().toString())
        put(COLUMN_TITLE, template.title)
        put(COLUMN_CONTENT, template.content)
        put(COLUMN_CATEGORY, template.category.name)
        put(COLUMN_VARIABLES, Gson().toJson(template.variables))
        put(COLUMN_CREATED_AT, template.createdAt)
        put(COLUMN_IS_CUSTOM, if (template.isCustom) 1 else 0)
        put(COLUMN_LAST_SYNCED_AT, System.currentTimeMillis())
    }

    private fun MessageTemplate.toContentValues(): ContentValues {
        return ContentValues().apply { put(this@toContentValues) }
    }

    fun getTemplatesByCategory(category: TemplateCategory): Flow<List<MessageTemplate>> = flow {
        val templates = mutableListOf<MessageTemplate>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TEMPLATES,
            null,
            "$COLUMN_CATEGORY = ?",
            arrayOf(category.name),
            null,
            null,
            "$COLUMN_CREATED_AT DESC"
        )

        cursor.use {
            val gson = Gson()
            val variablesType = object : TypeToken<List<String>>() {}.type
            
            while (it.moveToNext()) {
                templates.add(MessageTemplate(
                    id = it.getString(it.getColumnIndexOrThrow(COLUMN_ID)) ?: "",
                    title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE)),
                    content = it.getString(it.getColumnIndexOrThrow(COLUMN_CONTENT)),
                    category = TemplateCategory.valueOf(it.getString(it.getColumnIndexOrThrow(COLUMN_CATEGORY))),
                    variables = gson.fromJson(it.getString(it.getColumnIndexOrThrow(COLUMN_VARIABLES)), variablesType),
                    createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                    isCustom = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_CUSTOM)) == 1
                ))
            }
        }
        emit(templates)
    }.flowOn(Dispatchers.IO)

    fun insertTemplates(templates: List<MessageTemplate>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            templates.forEach { template ->
                db.insert(TABLE_TEMPLATES, null, template.toContentValues())
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertTemplate(template: MessageTemplate) {
        val db = writableDatabase
        db.insert(TABLE_TEMPLATES, null, template.toContentValues())
    }

    fun deleteTemplate(template: MessageTemplate) {
        val db = writableDatabase
        db.delete(TABLE_TEMPLATES, "$COLUMN_ID = ?", arrayOf(template.id))
    }

    fun deleteNonCustomTemplatesByCategory(category: TemplateCategory) {
        val db = writableDatabase
        db.delete(
            TABLE_TEMPLATES,
            "$COLUMN_CATEGORY = ? AND $COLUMN_IS_CUSTOM = ?",
            arrayOf(category.name, "0")
        )
    }

    fun getCustomTemplates(): Flow<List<MessageTemplate>> = flow {
        val templates = mutableListOf<MessageTemplate>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TEMPLATES,
            null,
            "$COLUMN_IS_CUSTOM = ?",
            arrayOf("1"),
            null,
            null,
            "$COLUMN_CREATED_AT DESC"
        )

        cursor.use {
            val gson = Gson()
            val variablesType = object : TypeToken<List<String>>() {}.type
            
            while (it.moveToNext()) {
                templates.add(MessageTemplate(
                    id = it.getString(it.getColumnIndexOrThrow(COLUMN_ID)),
                    title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE)),
                    content = it.getString(it.getColumnIndexOrThrow(COLUMN_CONTENT)),
                    category = TemplateCategory.valueOf(it.getString(it.getColumnIndexOrThrow(COLUMN_CATEGORY))),
                    variables = gson.fromJson(it.getString(it.getColumnIndexOrThrow(COLUMN_VARIABLES)), variablesType),
                    createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                    isCustom = true
                ))
            }
        }
        emit(templates)
    }.flowOn(Dispatchers.IO)

    fun getLastSyncTime(category: TemplateCategory): Long? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TEMPLATES,
            arrayOf("MAX($COLUMN_LAST_SYNCED_AT) as last_sync"),
            "$COLUMN_CATEGORY = ?",
            arrayOf(category.name),
            null,
            null,
            null
        )

        return cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                it.getLong(0)
            } else {
                null
            }
        }
    }
} 