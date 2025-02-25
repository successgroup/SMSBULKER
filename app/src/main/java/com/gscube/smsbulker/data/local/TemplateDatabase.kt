package com.gscube.smsbulker.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.gscube.smsbulker.data.model.MessageTemplate
import com.gscube.smsbulker.data.model.TemplateCategory
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID

class TemplateDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val stringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    fun getVariablesFromJson(json: String): List<String> {
        return stringListAdapter.fromJson(json) ?: emptyList()
    }

    fun variablesToJson(variables: List<String>): String {
        return stringListAdapter.toJson(variables)
    }

    companion object {
        private const val DATABASE_NAME = "templates.db"
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
        insertDefaultTemplates(db)
    }

    private fun insertDefaultTemplates(db: SQLiteDatabase) {
        val defaultTemplates = listOf(
            // General Templates
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Welcome Message",
                content = "Welcome {name}! Thank you for choosing our service.",
                category = TemplateCategory.GENERAL,
                variables = listOf("name"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Thank You Message",
                content = "Dear {name}, thank you for your continued support. We appreciate your business!",
                category = TemplateCategory.GENERAL,
                variables = listOf("name"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),

            // Church Templates
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Service Reminder",
                content = "Dear {name}, join us this Sunday at {time} for our special service: {theme}",
                category = TemplateCategory.CHURCH,
                variables = listOf("name", "time", "theme"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Prayer Meeting",
                content = "Hello {name}, you're invited to our prayer meeting on {date} at {time}. Topic: {topic}",
                category = TemplateCategory.CHURCH,
                variables = listOf("name", "date", "time", "topic"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),

            // School Templates
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Parent Meeting",
                content = "Dear {parentName}, please attend the parent-teacher meeting for {studentName} on {date} at {time}.",
                category = TemplateCategory.SCHOOL,
                variables = listOf("parentName", "studentName", "date", "time"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "School Event",
                content = "Dear {parentName}, {schoolName} invites you to {eventName} on {date}. Your child {studentName}'s participation is important.",
                category = TemplateCategory.SCHOOL,
                variables = listOf("parentName", "schoolName", "eventName", "date", "studentName"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),

            // Bank Templates
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Transaction Alert",
                content = "Dear {name}, a {transactionType} of {amount} was made on your account. Balance: {balance}",
                category = TemplateCategory.BANK,
                variables = listOf("name", "transactionType", "amount", "balance"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Account Statement",
                content = "Dear {name}, your account statement for {period} is ready. View it here: {link}",
                category = TemplateCategory.BANK,
                variables = listOf("name", "period", "link"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),

            // Club Templates
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Event Invitation",
                content = "Hi {name}! Join us for {eventName} at {venue} on {date}. RSVP by {rsvpDate}.",
                category = TemplateCategory.CLUB,
                variables = listOf("name", "eventName", "venue", "date", "rsvpDate"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Membership Renewal",
                content = "Dear {name}, your club membership expires on {date}. Renew now to continue enjoying benefits!",
                category = TemplateCategory.CLUB,
                variables = listOf("name", "date"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),

            // Business Templates
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Business Meeting",
                content = "Dear {name}, you're invited to a business meeting on {date} at {time}. Agenda: {agenda}",
                category = TemplateCategory.BUSINESS,
                variables = listOf("name", "date", "time", "agenda"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Quote Request",
                content = "Dear {name}, thank you for your quote request. Your reference number is {refNumber}. We'll respond within 24 hours.",
                category = TemplateCategory.BUSINESS,
                variables = listOf("name", "refNumber"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),

            // Marketing Templates
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Special Offer",
                content = "Hi {name}! Don't miss out on our special offer: {offer}. Valid until {date}.",
                category = TemplateCategory.MARKETTING,
                variables = listOf("name", "offer", "date"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "New Product Launch",
                content = "Dear {name}, we're excited to announce our new product: {product}! Learn more at {link}",
                category = TemplateCategory.MARKETTING,
                variables = listOf("name", "product", "link"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),

            // Notification Templates
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Order Confirmation",
                content = "Thank you {name}! Your order #{orderNumber} has been confirmed. Estimated delivery: {date}",
                category = TemplateCategory.NOTIFICATIONS,
                variables = listOf("name", "orderNumber", "date"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Delivery Status",
                content = "Hi {name}, your order #{orderNumber} has been {status}. Track here: {link}",
                category = TemplateCategory.NOTIFICATIONS,
                variables = listOf("name", "orderNumber", "status", "link"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),

            // Reminder Templates
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Appointment Reminder",
                content = "Hi {name}, this is a reminder for your appointment on {date} at {time}.",
                category = TemplateCategory.REMINDERS,
                variables = listOf("name", "date", "time"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Payment Due Reminder",
                content = "Dear {name}, this is a reminder that your payment of {amount} is due on {date}.",
                category = TemplateCategory.REMINDERS,
                variables = listOf("name", "amount", "date"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),

            // Alert Templates
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "System Maintenance",
                content = "Dear {name}, our system will be under maintenance on {date} from {startTime} to {endTime}.",
                category = TemplateCategory.ALERT,
                variables = listOf("name", "date", "startTime", "endTime"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            ),
            MessageTemplate(
                id = UUID.randomUUID().toString(),
                title = "Security Alert",
                content = "Security Alert: A new login to your account was detected from {location}. Time: {time}",
                category = TemplateCategory.ALERT,
                variables = listOf("location", "time"),
                createdAt = System.currentTimeMillis(),
                isCustom = false
            )
        )

        defaultTemplates.forEach { template ->
            val values = ContentValues().apply {
                put(COLUMN_ID, template.id)
                put(COLUMN_TITLE, template.title)
                put(COLUMN_CONTENT, template.content)
                put(COLUMN_CATEGORY, template.category.name)
                put(COLUMN_VARIABLES, stringListAdapter.toJson(template.variables))
                put(COLUMN_CREATED_AT, template.createdAt)
                put(COLUMN_IS_CUSTOM, if (template.isCustom) 1 else 0)
                put(COLUMN_LAST_SYNCED_AT, System.currentTimeMillis())
            }
            db.insert(TABLE_TEMPLATES, null, values)
        }
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
        put(COLUMN_VARIABLES, stringListAdapter.toJson(template.variables))
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
            while (it.moveToNext()) {
                templates.add(MessageTemplate(
                    id = it.getString(it.getColumnIndexOrThrow(COLUMN_ID)) ?: "",
                    title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE)),
                    content = it.getString(it.getColumnIndexOrThrow(COLUMN_CONTENT)),
                    category = TemplateCategory.valueOf(it.getString(it.getColumnIndexOrThrow(COLUMN_CATEGORY))),
                    variables = stringListAdapter.fromJson(it.getString(it.getColumnIndexOrThrow(COLUMN_VARIABLES))) ?: emptyList(),
                    createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                    isCustom = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_CUSTOM)) == 1
                ))
            }
        }
        emit(templates)
    }.flowOn(Dispatchers.IO)

    fun insertTemplate(template: MessageTemplate) {
        writableDatabase.use { db ->
            db.insertWithOnConflict(
                TABLE_TEMPLATES,
                null,
                template.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun insertTemplates(templates: List<MessageTemplate>) {
        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                templates.forEach { template ->
                    db.insertWithOnConflict(
                        TABLE_TEMPLATES,
                        null,
                        template.toContentValues(),
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun deleteTemplate(template: MessageTemplate) {
        writableDatabase.use { db ->
            db.delete(
                TABLE_TEMPLATES,
                "$COLUMN_ID = ?",
                arrayOf(template.id)
            )
        }
    }

    fun clearAllTemplates() {
        writableDatabase.use { db ->
            db.delete(TABLE_TEMPLATES, null, null)
        }
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
            while (it.moveToNext()) {
                templates.add(MessageTemplate(
                    id = it.getString(it.getColumnIndexOrThrow(COLUMN_ID)),
                    title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE)),
                    content = it.getString(it.getColumnIndexOrThrow(COLUMN_CONTENT)),
                    category = TemplateCategory.valueOf(it.getString(it.getColumnIndexOrThrow(COLUMN_CATEGORY))),
                    variables = stringListAdapter.fromJson(it.getString(it.getColumnIndexOrThrow(COLUMN_VARIABLES))) ?: emptyList(),
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

    fun deleteNonCustomTemplatesByCategory(category: TemplateCategory) {
        writableDatabase.use { db ->
            db.delete(
                TABLE_TEMPLATES,
                "$COLUMN_CATEGORY = ? AND $COLUMN_IS_CUSTOM = ?",
                arrayOf(category.name, "0")
            )
        }
    }
}