package com.ryosoftware.contact_dates_notifier.data

import android.content.Context
import android.provider.ContactsContract
import com.ryosoftware.contact_dates_notifier.data.local.ApplicationDatabase
import com.ryosoftware.contact_dates_notifier.data.model.Contact
import com.ryosoftware.contact_dates_notifier.data.model.DeviceContact
import com.ryosoftware.utilities.LogUtilities


object DeviceContactsDriver {
    private val ACCOUNT_NAME_CLEAN_REGEX = Regex(" #\\d+$")
    private const val CONTACT_ID_ORDER = 0
    private const val DISPLAY_NAME_ORDER = 1
    private const val DATE_ORDER = 2
    private const val EVENT_TYPE_ORDER = 3
    private const val EVENT_LABEL_ORDER = 4
    private const val RAW_CONTACT_ID_ORDER = 5

    private val PROJECTION = arrayOf(
        ContactsContract.CommonDataKinds.Event.CONTACT_ID,
        ContactsContract.Contacts.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Event.START_DATE,
        ContactsContract.CommonDataKinds.Event.TYPE,
        ContactsContract.CommonDataKinds.Event.LABEL,
        ContactsContract.Data.RAW_CONTACT_ID
    )

    private const val DATE_FIELDS_SEPARATOR = "-"
    private const val DATE_TIME_SEPARATOR = "T"

    private var cachedContacts: List<Contact>? = null
    private var cachedAccounts: List<String>? = null
    private var cacheTimestamp: Long = 0L
    private const val CACHE_TTL_MS = 30_000L

    private fun isCacheValid(): Boolean =
        System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS

    private fun getEvent(date: String?, description: String?): Contact.Event? {
        if (!date.isNullOrEmpty()) {
            try {
                var values = date.split(DATE_TIME_SEPARATOR).toTypedArray()
                if (values.isNotEmpty()) {
                    values = values[0].split(DATE_FIELDS_SEPARATOR).toTypedArray()
                    if (values.isNotEmpty()) {
                        return when (values.size) {
                            3 -> Contact.Event(
                                description ?: "",
                                values[2].toInt(),
                                values[1].toInt(),
                                values[0].toInt()
                            )
                            4 -> Contact.Event(
                                description ?: "",
                                values[3].toInt(),
                                values[2].toInt()
                            )
                            else -> null
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtilities.show(DeviceContactsDriver::class.java, e)
            }
        }
        return null
    }

    private fun getContactById(contacts: List<DeviceContact>, identifier: Long): DeviceContact? {
        return contacts.find { it.databaseContactIdentifier == identifier }
    }

    private fun getContactImmutableIdByDatabaseContactIdentifier(context: Context, databaseContactIdentifier: Long): String? {
        if (databaseContactIdentifier >= 0) {
            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
                    "${ContactsContract.Contacts._ID}=?",
                    arrayOf(databaseContactIdentifier.toString()),
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) return it.getString(0)
                }
            } catch (e: Exception) {
                LogUtilities.show(DeviceContactsDriver::class.java, e)
            }
        }
        return null
    }

    fun invalidateCache() {
        cachedContacts = null
        cachedAccounts = null
        cacheTimestamp = 0L
    }

    fun get(context: Context): List<Contact> {
        if (isCacheValid() && cachedContacts != null) return cachedContacts!!
        val contacts = mutableListOf<DeviceContact>()

        val notesMap = mutableMapOf<String, String>()
        try {
            notesMap.putAll(ApplicationDatabase.getInstance(context).loadDeviceContactsNotes())
        } catch (e: Exception) {
            LogUtilities.show(DeviceContactsDriver::class.java, e)
        }

        val groupsMap = mutableMapOf<Long, String>()
        try {
            context.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
                "${ContactsContract.Groups.DELETED}!=?",
                arrayOf("1"),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(1)
                    if (!title.isNullOrBlank()) {
                        groupsMap[cursor.getLong(0)] = title
                    }
                }
            }
        } catch (_: Exception) { }

        val contactGroupsMap = mutableMapOf<Long, MutableList<String>>()
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID
                ),
                "${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val contactId = cursor.getLong(0)
                        val groupId = cursor.getLong(1)
                        groupsMap[groupId]?.takeIf { it.isNotBlank() }?.let { groupName ->
                            contactGroupsMap.getOrPut(contactId) { mutableListOf() }.add(groupName)
                        }
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }

        val rawContactsAccountMap = mutableMapOf<Long, String?>()
        try {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.RawContacts._ID,
                    ContactsContract.RawContacts.ACCOUNT_NAME
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    rawContactsAccountMap[cursor.getLong(0)] = cursor.getString(1)?.replace(ACCOUNT_NAME_CLEAN_REGEX, "")
                }
            }
        } catch (_: Exception) { }

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                PROJECTION,
                "${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE),
                null
            )
            cursor?.use {
                if (it.count > 0) {
                    it.moveToFirst()
                    while (!it.isAfterLast) {
                        val eventType = it.getInt(EVENT_TYPE_ORDER)
                        var eventLabel = it.getString(EVENT_LABEL_ORDER)
                        if (eventLabel.isNullOrEmpty()) {
                            eventLabel = when (eventType) {
                                ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> context.getString(com.ryosoftware.contact_dates_notifier.R.string.anniversary)
                                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> context.getString(com.ryosoftware.contact_dates_notifier.R.string.birthday)
                                else -> eventLabel
                            }
                        }
                        val event = getEvent(it.getString(DATE_ORDER), eventLabel)
                        if (event != null) {
                            val contactId = it.getLong(CONTACT_ID_ORDER)
                            var contact = getContactById(contacts, contactId)
                            if (contact == null) {
                                val immutableId = getContactImmutableIdByDatabaseContactIdentifier(context, contactId)
                                val rawContactId = it.getLong(RAW_CONTACT_ID_ORDER)
                                contact = DeviceContact(
                                    databaseContactIdentifier = contactId,
                                    immutableContactIdentifier = immutableId,
                                    name = it.getString(DISPLAY_NAME_ORDER) ?: "",
                                    accountName = rawContactsAccountMap[rawContactId],
                                    groups = contactGroupsMap[contactId],
                                    notes = notesMap[immutableId]
                                )
                                contacts.add(contact)
                            }
                            if (eventType == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) contact.birthEvent = event
                            contact.addEvent(event)
                        }
                        it.moveToNext()
                    }
                }
            }
        } catch (e: Exception) {
            LogUtilities.show(DeviceContactsDriver::class.java, e)
        }
        cachedContacts = contacts
        cachedAccounts = contacts.mapNotNull { it.accountName }
            .filter { it.isNotEmpty() }.distinct()
        cacheTimestamp = System.currentTimeMillis()
        return contacts
    }

    fun getAccounts(context: Context): List<String> {
        if (isCacheValid() && cachedAccounts != null) return cachedAccounts!!
        val accounts = mutableListOf<String>()
        try {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val account = cursor.getString(0)?.replace(ACCOUNT_NAME_CLEAN_REGEX, "")
                    if (!account.isNullOrEmpty() && account !in accounts) accounts.add(account)
                }
            }
        } catch (e: Exception) {
            LogUtilities.show(DeviceContactsDriver::class.java, e)
        }
        cachedAccounts = accounts
        cacheTimestamp = System.currentTimeMillis()
        return accounts
    }
}
