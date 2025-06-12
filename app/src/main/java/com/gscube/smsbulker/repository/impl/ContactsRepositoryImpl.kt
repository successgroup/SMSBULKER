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
import com.gscube.smsbulker.data.model.SkippedContact
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
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Load initial data if database is empty
        coroutineScope.launch {
            try {
                val contacts = contactDao.getAllContacts().firstOrNull() ?: emptyList()
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

    override suspend fun importContactsFromCsv(uri: Uri): List<Contact> = withContext(Dispatchers.IO) {
        val importedContacts = mutableListOf<Contact>()
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            var headers: List<String>? = null
            var phoneIndex = -1
            var nameIndex = -1
            var groupIndex = -1

            reader.forEachLine { line ->
                val values = line.split(",").map { it.trim() }
                
                if (headers == null) {
                    // Parse headers
                    headers = values
                    phoneIndex = headers!!.indexOfFirst { it.contains("phone", ignoreCase = true) }
                    nameIndex = headers!!.indexOfFirst { it.contains("name", ignoreCase = true) }
                    groupIndex = headers!!.indexOfFirst { it.contains("group", ignoreCase = true) }
                    
                    if (phoneIndex == -1 || nameIndex == -1) {
                        throw IllegalArgumentException("CSV must have 'name' and 'phone' columns")
                    }
                } else {
                    // Parse contact data
                    if (values.size >= maxOf(phoneIndex, nameIndex) + 1) {
                        val phone = values[phoneIndex].trim('"', ' ')
                        val name = values[nameIndex].trim('"', ' ')
                        val group = if (groupIndex != -1 && values.size > groupIndex) {
                            values[groupIndex].trim('"', ' ')
                        } else ""
                        
                        // Create variables map from other columns
                        val variables = mutableMapOf<String, String>()
                        headers!!.forEachIndexed { index, header ->
                            if (index != phoneIndex && index != nameIndex && index != groupIndex && values.size > index) {
                                variables[header] = values[index].trim('"', ' ')
                            }
                        }
                        
                        val contact = Contact(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            phoneNumber = phone,
                            group = group,
                            variables = variables
                        )
                        
                        // Save to database using non-suspending function
                        contactDao.insertContactSync(ContactEntity.fromContact(contact))
                        importedContacts.add(contact)
                    }
                }
            }
        } ?: throw IllegalArgumentException("Could not read CSV file")
        
        importedContacts
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

    override suspend fun exportSkippedContactsToCSV(skippedContacts: List<SkippedContact>): Unit = withContext(Dispatchers.IO) {
        try {
            val fileName = "skipped_contacts_${System.currentTimeMillis()}.csv"
            // For now, we'll just log the export since file creation needs proper implementation
            android.util.Log.d("ContactsRepository", "Exporting ${skippedContacts.size} skipped contacts to $fileName")
            
            // Simple implementation that creates a CSV string
            val csvContent = buildString {
                appendLine("Name,Phone Number,Original Phone Number,Reason,Skipped At")
                skippedContacts.forEach { contact ->
                    appendLine("\"${contact.name}\",\"${contact.phoneNumber}\",\"${contact.originalPhoneNumber}\",\"${contact.reason}\",\"${contact.skippedAt}\"")
                }
            }
            
            android.util.Log.d("ContactsRepository", "CSV Content: $csvContent")
            
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepository", "Failed to export skipped contacts: ${e.message}")
            throw Exception("Failed to export skipped contacts: ${e.message}")
        }
    }

    private fun createCsvFile(fileName: String): Uri {
        // Implementation depends on your file creation strategy
        // This is a simplified version - you may need to adjust based on your app's file handling
        throw NotImplementedError("File creation implementation needed")
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