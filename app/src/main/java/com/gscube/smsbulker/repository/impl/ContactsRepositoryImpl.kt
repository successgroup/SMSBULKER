package com.gscube.smsbulker.repository.impl

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.gscube.smsbulker.data.local.AppDatabase
import com.gscube.smsbulker.data.local.ContactEntity
import com.gscube.smsbulker.data.model.Contact
import com.gscube.smsbulker.di.AppScope
import com.gscube.smsbulker.repository.ContactsRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
@ContributesBinding(AppScope::class)
class ContactsRepositoryImpl @Inject constructor(
    @Named("applicationContext") private val context: Context
) : ContactsRepository {
    private val database = AppDatabase.getInstance(context)
    private val contactDao = database.contactDao()

    init {
        // Load initial data if database is empty
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contacts = contactDao.getAllContacts().first()
                if (contacts.isEmpty()) {
                    // Add some sample contacts
                    val sampleContacts = listOf(
                        Contact(
                            id = UUID.randomUUID().toString(),
                            name = "John Doe",
                            phoneNumber = "+1234567890",
                            group = "Friends",
                            variables = mapOf("nickname" to "Johnny")
                        ),
                        Contact(
                            id = UUID.randomUUID().toString(),
                            name = "Jane Smith",
                            phoneNumber = "+0987654321",
                            group = "Family",
                            variables = mapOf("birthday" to "Jan 1")
                        )
                    )
                    sampleContacts.forEach { contact ->
                        contactDao.insertContact(ContactEntity.fromContact(contact))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getContactsFlow(): Flow<List<Contact>> {
        return contactDao.getAllContacts()
            .map { entities -> entities.map { it.toContact() } }
            .flowOn(Dispatchers.IO)
            .catch { e -> 
                // Log error but don't stop the flow
                e.printStackTrace()
                emit(emptyList())
            }
    }

    override suspend fun getContacts(): List<Contact> = withContext(Dispatchers.IO) {
        contactDao.getAllContacts().first().map { it.toContact() }
    }

    override suspend fun saveContact(contact: Contact) = withContext(Dispatchers.IO) {
        contactDao.insertContact(ContactEntity.fromContact(contact))
    }

    override suspend fun deleteContact(contact: Contact) {
        withContext(Dispatchers.IO) {
            contact.id?.let { id ->
                contactDao.deleteContactById(id)
            }
        }
    }

    override suspend fun importContactsFromCsv(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            val contacts = mutableListOf<Contact>()
            var headers: List<String>? = null
            var phoneIndex = -1
            var nameIndex = -1

            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine

                val columns = line.split(",").map { it.trim().lowercase() }
                
                if (headers == null) {
                    // Parse header row and find name and phone columns
                    headers = columns
                    
                    // Look for phone column - common variations
                    phoneIndex = columns.indexOfFirst { col ->
                        col.contains("phone") || col.contains("mobile") || 
                        col.contains("cell") || col.contains("tel") ||
                        col.contains("number")
                    }
                    
                    // Look for name column - common variations
                    nameIndex = columns.indexOfFirst { col ->
                        col.contains("name") || col.contains("contact") ||
                        col.contains("person") || col.contains("full")
                    }
                    
                    // If we can't find the columns, try to find them by analyzing the data
                    if (phoneIndex == -1 || nameIndex == -1) {
                        // We'll set these in the first data row
                        return@forEachLine
                    }
                    return@forEachLine
                }

                // For the first data row, if we haven't found the columns yet, try to detect them
                if ((phoneIndex == -1 || nameIndex == -1) && columns.isNotEmpty()) {
                    // Look for a column that matches phone number pattern
                    phoneIndex = columns.indexOfFirst { col ->
                        col.replace("[^0-9+]".toRegex(), "").length >= 10
                    }
                    
                    // If still not found, look for any column with numbers
                    if (phoneIndex == -1) {
                        phoneIndex = columns.indexOfFirst { col ->
                            col.any { it.isDigit() }
                        }
                    }
                    
                    // For name, take any non-phone column
                    nameIndex = columns.indices.firstOrNull { it != phoneIndex } ?: 0
                    
                    // If we still couldn't find a phone column, just take any other column
                    if (phoneIndex == -1) {
                        phoneIndex = columns.indices.firstOrNull { it != nameIndex } ?: 1
                    }
                }

                try {
                    // Get phone and name from detected columns, with fallback to first available columns
                    val phone = when {
                        phoneIndex >= 0 && phoneIndex < columns.size -> columns[phoneIndex]
                        columns.size > 1 -> columns[1]
                        columns.isNotEmpty() -> columns[0]
                        else -> return@forEachLine
                    }.replace("[^0-9+]".toRegex(), "")

                    val name = when {
                        nameIndex >= 0 && nameIndex < columns.size -> columns[nameIndex]
                        columns.size > 1 -> columns[0]
                        columns.isNotEmpty() -> columns[0]
                        else -> return@forEachLine
                    }

                    // Skip if phone or name is empty
                    if (phone.isBlank() || name.isBlank()) return@forEachLine

                    // Get additional fields as variables
                    val variables = mutableMapOf<String, String>()
                    headers!!.forEachIndexed { index, header ->
                        if (index != phoneIndex && index != nameIndex && 
                            index < columns.size && 
                            header.isNotBlank() &&
                            columns[index].isNotBlank()
                        ) {
                            variables[header] = columns[index]
                        }
                    }

                    contacts.add(
                        Contact(
                            id = UUID.randomUUID().toString(),
                            phoneNumber = phone,
                            name = name,
                            group = variables.remove("group") ?: "",
                            variables = variables
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid rows instead of throwing error
                    return@forEachLine
                }
            }

            if (contacts.isEmpty()) {
                throw IllegalArgumentException("No valid contacts found in the CSV file. Please ensure the file contains name and phone number columns.")
            }

            // Save valid contacts to local storage
            contacts.forEach { saveContact(it) }
        } ?: throw IllegalArgumentException("Could not open CSV file")
    }

    override suspend fun exportContactsToCsv() = withContext(Dispatchers.IO) {
        val contacts = getContacts()
        if (contacts.isEmpty()) {
            throw IllegalStateException("No contacts to export")
        }

        // Get all unique variable keys
        val variableKeys = contacts.flatMap { it.variables.keys }.distinct().sorted()
        
        // Create CSV header
        val headers = listOf("phone", "name", "group") + variableKeys
        
        // Create CSV content
        val csvContent = buildString {
            // Write header
            appendLine(headers.joinToString(","))
            
            // Write data rows
            contacts.forEach { contact ->
                val row = mutableListOf(
                    contact.phoneNumber,
                    contact.name,
                    contact.group
                )
                
                // Add variables in the same order as headers
                variableKeys.forEach { key ->
                    row.add(contact.variables[key] ?: "")
                }
                
                appendLine(row.joinToString(","))
            }
        }

        // Save to Downloads directory
        val fileName = "contacts_${System.currentTimeMillis()}.csv"
        context.contentResolver.openOutputStream(Uri.parse("content://downloads/$fileName"))?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(csvContent)
            }
        } ?: throw IllegalStateException("Could not create output file")
    }

    override suspend fun importFromPhoneContacts() = withContext(Dispatchers.IO) {
        val contentResolver: ContentResolver = context.contentResolver
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )

        cursor?.use {
            val contacts = mutableListOf<Contact>()
            val phoneNumberColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val nameColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val groupColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)

            while (it.moveToNext()) {
                val phoneNumber = it.getString(phoneNumberColumn)?.replace("[^0-9+]".toRegex(), "") ?: continue
                val name = it.getString(nameColumn) ?: continue
                val groupId = if (groupColumn >= 0) it.getString(groupColumn) else null

                // Get group name if groupId exists
                val group = if (groupId != null) {
                    getGroupName(contentResolver, groupId)
                } else ""

                contacts.add(
                    Contact(
                        id = UUID.randomUUID().toString(),
                        phoneNumber = phoneNumber,
                        name = name,
                        group = group
                    )
                )
            }

            // Save contacts to local storage
            contacts.forEach { contact -> saveContact(contact) }
        } ?: throw IllegalStateException("Could not access phone contacts")
    }

    override suspend fun exportToPhoneContacts(contacts: List<Contact>) = withContext(Dispatchers.IO) {
        val contentResolver: ContentResolver = context.contentResolver
        
        contacts.forEach { contact ->
            val values = ContentValues().apply {
                put(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                put(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
            }

            val rawContactUri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, values)
            val rawContactId = rawContactUri?.lastPathSegment?.toLongOrNull() ?: return@forEach

            // Add name
            ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
            }.let {
                contentResolver.insert(ContactsContract.Data.CONTENT_URI, it)
            }

            // Add phone number
            ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phoneNumber)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }.let {
                contentResolver.insert(ContactsContract.Data.CONTENT_URI, it)
            }
        }
    }

    private fun getGroupName(contentResolver: ContentResolver, groupId: String): String {
        val groupCursor = contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups.TITLE),
            "${ContactsContract.Groups._ID} = ?",
            arrayOf(groupId),
            null
        )

        return groupCursor?.use {
            if (it.moveToFirst()) {
                it.getString(it.getColumnIndex(ContactsContract.Groups.TITLE)) ?: ""
            } else ""
        } ?: ""
    }
}