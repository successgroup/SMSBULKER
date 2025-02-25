package com.gscube.smsbulker.repository

import android.net.Uri
import com.gscube.smsbulker.data.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactsRepository {
    fun getContactsFlow(): Flow<List<Contact>>
    suspend fun getContacts(): List<Contact>
    suspend fun saveContact(contact: Contact)
    suspend fun deleteContact(contact: Contact)
    suspend fun importContactsFromCsv(uri: Uri): List<Contact>
    suspend fun exportContactsToCsv()
    suspend fun importFromPhoneContacts()
    suspend fun exportToPhoneContacts(contacts: List<Contact>)
}