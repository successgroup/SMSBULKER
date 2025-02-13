package com.gscube.smsbulker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gscube.smsbulker.data.model.Contact
import java.lang.reflect.Type

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val id: String,
    val phoneNumber: String,
    val name: String,
    val group: String = "",
    val variables: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toContact(): Contact = Contact(
        id = id,
        phoneNumber = phoneNumber,
        name = name,
        group = group,
        variables = variables
    )

    companion object {
        fun fromContact(contact: Contact): ContactEntity = ContactEntity(
            id = contact.id ?: System.currentTimeMillis().toString(),
            phoneNumber = contact.phoneNumber,
            name = contact.name,
            group = contact.group,
            variables = contact.variables
        )
    }
}

class ContactConverters {
    private val gson = Gson()
    private val mapType: Type = object : TypeToken<Map<String, String>>() {}.type

    @TypeConverter
    fun fromString(value: String?): Map<String, String> {
        if (value.isNullOrEmpty()) return emptyMap()
        return try {
            gson.fromJson<Map<String, String>>(value, mapType) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @TypeConverter
    fun fromMap(map: Map<String, String>?): String {
        return try {
            gson.toJson(map ?: emptyMap<String, String>())
        } catch (e: Exception) {
            "{}"
        }
    }
}
