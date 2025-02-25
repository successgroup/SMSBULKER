package com.gscube.smsbulker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.gscube.smsbulker.data.local.converters.ContactConverters
import com.gscube.smsbulker.data.model.Contact

@Entity(tableName = "contacts")
@TypeConverters(ContactConverters::class)
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
