package com.ryosoftware.contact_dates_notifier.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContactEvent
import com.ryosoftware.utilities.LogUtilities

class ApplicationDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "application"
        private const val DATABASE_VERSION = 5
        private const val APPLICATION_CONTACTS_TABLE_NAME = "application_contacts"
        private const val APPLICATION_EVENTS_TABLE_NAME = "application_events"
        private const val DEVICE_CONTACTS_TABLE_NAME = "device_contacts"

        const val CONTACT_ID = "_id"
        const val CONTACT_NAME = "name"
        const val CONTACT_DISABLED = "disabled"
        const val CONTACT_NOTES = "notes"
        const val EVENT_ID = "_id"
        const val CONTACT_EVENT_OWNER_ID = "contact"
        const val EVENT_DATE = "date"
        const val EVENT_DESCRIPTION = "description"
        const val EVENT_DISABLED = "disabled"

        const val DEVICE_CONTACT_ID = "_id"
        const val DEVICE_CONTACT_CONTACT_ID = "contact_id"
        const val DEVICE_CONTACT_NOTES = "notes"

        const val DEVICE_CONTACT_ID_ORDER = 0
        const val DEVICE_CONTACT_IMMUTABLE_CONTACT_ID_ORDER = 1
        const val DEVICE_CONTACT_NOTES_ORDER = 2

        const val ROWID_ERROR = -1L
        const val CONTACT_ID_ORDER = 0
        const val CONTACT_NAME_ORDER = 1
        const val CONTACT_DISABLED_ORDER = 2
        const val CONTACT_NOTES_ORDER = 3
        const val EVENT_ID_ORDER = 0
        const val CONTACT_EVENT_OWNER_ID_ORDER = 1
        const val EVENT_DATE_ORDER = 2
        const val EVENT_DESCRIPTION_ORDER = 3
        const val EVENT_DISABLED_ORDER = 4

        @Volatile
        private var INSTANCE: ApplicationDatabase? = null

        fun getInstance(context: Context): ApplicationDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApplicationDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getPartsFromDate(date: String): IntArray {
            val parts = date.split("-")
            return intArrayOf(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        }
    }

    private val db: SQLiteDatabase get() = writableDatabase

    private fun getDateFromParts(day: Int, month: Int, year: Int): String =
        "$year-$month-$day"

    override fun onCreate(sqliteDatabase: SQLiteDatabase) {
        LogUtilities.show(this, "Creating database")
        sqliteDatabase.execSQL("CREATE TABLE $APPLICATION_CONTACTS_TABLE_NAME ($CONTACT_ID INTEGER PRIMARY KEY AUTOINCREMENT, $CONTACT_NAME TEXT, $CONTACT_DISABLED INTEGER DEFAULT 0, $CONTACT_NOTES TEXT)")
        sqliteDatabase.execSQL("CREATE TABLE $APPLICATION_EVENTS_TABLE_NAME ($EVENT_ID INTEGER PRIMARY KEY AUTOINCREMENT, $CONTACT_EVENT_OWNER_ID INTEGER, $EVENT_DATE TEXT, $EVENT_DESCRIPTION TEXT, $EVENT_DISABLED INTEGER DEFAULT 0)")
        sqliteDatabase.execSQL("CREATE TABLE $DEVICE_CONTACTS_TABLE_NAME ($DEVICE_CONTACT_ID INTEGER PRIMARY KEY AUTOINCREMENT, $DEVICE_CONTACT_CONTACT_ID TEXT UNIQUE NOT NULL, $DEVICE_CONTACT_NOTES TEXT)")
        LogUtilities.show(this, "Database created")
    }

    override fun onUpgrade(sqliteDatabase: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            sqliteDatabase.execSQL("ALTER TABLE contacts ADD COLUMN $CONTACT_DISABLED INTEGER DEFAULT 0")
        }
        if (oldVersion < 3) {
            sqliteDatabase.execSQL("ALTER TABLE events ADD COLUMN $EVENT_DISABLED INTEGER DEFAULT 0")
        }
        if (oldVersion < 4) {
            sqliteDatabase.execSQL("ALTER TABLE contacts RENAME TO $APPLICATION_CONTACTS_TABLE_NAME")
            sqliteDatabase.execSQL("ALTER TABLE events RENAME TO $APPLICATION_EVENTS_TABLE_NAME")
        }
        if (oldVersion < 5) {
            sqliteDatabase.execSQL("ALTER TABLE $APPLICATION_CONTACTS_TABLE_NAME ADD COLUMN $CONTACT_NOTES TEXT")
            sqliteDatabase.execSQL("CREATE TABLE $DEVICE_CONTACTS_TABLE_NAME ($DEVICE_CONTACT_ID INTEGER PRIMARY KEY AUTOINCREMENT, $DEVICE_CONTACT_CONTACT_ID TEXT UNIQUE NOT NULL, $DEVICE_CONTACT_NOTES TEXT)")
        }
    }

    override fun onDowngrade(sqliteDatabase: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Throws(Exception::class)
    private fun add(table: String, values: ContentValues): Long {
        return db.insert(table, null, values)
    }

    @Throws(Exception::class)
    private fun update(table: String, selection: String?, selectionArgs: Array<String>?,
                       values: ContentValues): Int {
        return db.update(table, values, selection, selectionArgs)
    }

    @Throws(Exception::class)
    private fun update(table: String, id: Long, values: ContentValues): Boolean {
        return update(table, "$CONTACT_ID=?", arrayOf(id.toString()), values) != 0
    }

    @Throws(Exception::class)
    private fun delete(table: String, selection: String?, selectionArgs: Array<String>?): Int {
        return db.delete(table, selection, selectionArgs)
    }

    @Throws(Exception::class)
    private fun get(table: String, projection: Array<String>?, selection: String?,
                    selectionArgs: Array<String>?, orderBy: String?): Cursor? {
        return db.query(true, table, projection, selection, selectionArgs, null, null, orderBy, null)
    }

    fun beginTransaction() {
        db.beginTransaction()
    }

    fun commitTransaction() {
        db.setTransactionSuccessful()
        db.endTransaction()
    }

    @Throws(Exception::class)
    fun addContact(name: String, disabled: Boolean = false, notes: String = ""): Long {
        val values = ContentValues().apply {
            put(CONTACT_NAME, name)
            put(CONTACT_DISABLED, if (disabled) 1 else 0)
            put(CONTACT_NOTES, notes)
        }
        return add(APPLICATION_CONTACTS_TABLE_NAME, values)
    }

    @Throws(Exception::class)
    fun updateContactNotes(id: Long, notes: String): Boolean {
        val values = ContentValues().apply { put(CONTACT_NOTES, notes) }
        return update(APPLICATION_CONTACTS_TABLE_NAME, id, values)
    }

    @Throws(Exception::class)
    fun updateContactDisabled(id: Long, disabled: Boolean): Boolean {
        val values = ContentValues().apply { put(CONTACT_DISABLED, if (disabled) 1 else 0) }
        return update(APPLICATION_CONTACTS_TABLE_NAME, id, values)
    }

    @Throws(Exception::class)
    fun updateContact(id: Long, name: String): Boolean {
        val values = ContentValues().apply { put(CONTACT_NAME, name) }
        return update(APPLICATION_CONTACTS_TABLE_NAME, id, values)
    }

    @Throws(Exception::class)
    fun deleteContact(id: Long): Boolean {
        return delete(APPLICATION_CONTACTS_TABLE_NAME, "$CONTACT_ID=?", arrayOf(id.toString())) == 1
    }

    @Throws(Exception::class)
    fun getContact(id: Long): Cursor? {
        return get(APPLICATION_CONTACTS_TABLE_NAME, null, "$CONTACT_ID=?", arrayOf(id.toString()), null)
    }

    @Throws(Exception::class)
    fun getContacts(): Cursor? {
        return get(APPLICATION_CONTACTS_TABLE_NAME, null, null, null, null)
    }

    @Throws(Exception::class)
    fun addEvent(contactId: Long, day: Int, month: Int, year: Int, description: String, disabled: Boolean = false): Long {
        val values = ContentValues().apply {
            put(CONTACT_EVENT_OWNER_ID, contactId)
            put(EVENT_DATE, getDateFromParts(day, month, year))
            put(EVENT_DESCRIPTION, description)
            put(EVENT_DISABLED, if (disabled) 1 else 0)
        }
        return add(APPLICATION_EVENTS_TABLE_NAME, values)
    }

    @Throws(Exception::class)
    fun deleteEvent(id: Long): Boolean {
        return delete(APPLICATION_EVENTS_TABLE_NAME, "$EVENT_ID=?", arrayOf(id.toString())) == 1
    }

    @Throws(Exception::class)
    fun deleteEvents(contactId: Long): Int {
        return delete(APPLICATION_EVENTS_TABLE_NAME, "$CONTACT_EVENT_OWNER_ID=?", arrayOf(contactId.toString()))
    }

    @Throws(Exception::class)
    fun updateEvent(id: Long, day: Int, month: Int, year: Int, description: String): Boolean {
        val values = ContentValues().apply {
            put(EVENT_DATE, getDateFromParts(day, month, year))
            put(EVENT_DESCRIPTION, description)
        }
        return update(APPLICATION_EVENTS_TABLE_NAME, id, values)
    }

    @Throws(Exception::class)
    fun updateEventDisabled(eventId: Long, disabled: Boolean): Boolean {
        val values = ContentValues().apply { put(EVENT_DISABLED, if (disabled) 1 else 0) }
        return update(APPLICATION_EVENTS_TABLE_NAME, eventId, values)
    }

    @Throws(Exception::class)
    fun getEvent(eventId: Long): Cursor? {
        return get(APPLICATION_EVENTS_TABLE_NAME, null, "$EVENT_ID=?", arrayOf(eventId.toString()), null)
    }

    @Throws(Exception::class)
    fun getEvents(contactId: Long): Cursor? {
        return get(APPLICATION_EVENTS_TABLE_NAME, null, "$CONTACT_EVENT_OWNER_ID=?", arrayOf(contactId.toString()), null)
    }

    fun getDisabledEventsKeys(contactId: Long): Set<String> {
        val keys = mutableSetOf<String>()
        try {
            val cursor = get(APPLICATION_EVENTS_TABLE_NAME, null,
                "$CONTACT_EVENT_OWNER_ID=? AND $EVENT_DISABLED=?",
                arrayOf(contactId.toString(), "1"), null)
            cursor?.use {
                if (it.count != 0) {
                    it.moveToFirst()
                    while (!it.isAfterLast) {
                        val parts = getPartsFromDate(it.getString(EVENT_DATE_ORDER))
                        val key = "${parts[0]}-${parts[1]}-${parts[2]}:${it.getString(EVENT_DESCRIPTION_ORDER)}"
                        keys.add(key)
                        it.moveToNext()
                    }
                }
            }
        } catch (e: Exception) {
            LogUtilities.show(ApplicationDatabase::class.java, e)
        }
        return keys
    }

    fun disableEventByKey(contactId: Long, day: Int, month: Int, year: Int, description: String, disabled: Boolean) {
        try {
            val cursor = get(APPLICATION_EVENTS_TABLE_NAME, null,
                "$CONTACT_EVENT_OWNER_ID=? AND $EVENT_DESCRIPTION=?",
                arrayOf(contactId.toString(), description), null)
            cursor?.use {
                while (!it.isAfterLast) {
                    val dateStr = it.getString(EVENT_DATE_ORDER)
                    val parts = getPartsFromDate(dateStr)
                    if (parts[0] == day && parts[1] == month && parts[2] == year) {
                        updateEventDisabled(it.getLong(EVENT_ID_ORDER), disabled)
                        return
                    }
                    it.moveToNext()
                }
            }
        } catch (e: Exception) {
            LogUtilities.show(ApplicationDatabase::class.java, e)
        }
    }

    private fun loadEvents(contactId: Long): List<ApplicationContactEvent> {
        val events = mutableListOf<ApplicationContactEvent>()
        try {
            val cursor = getEvents(contactId)
            cursor?.use {
                if (it.count != 0) {
                    it.moveToFirst()
                    while (!it.isAfterLast) {
                        val dateParts = getPartsFromDate(it.getString(EVENT_DATE_ORDER))
                        events.add(ApplicationContactEvent(
                            id = it.getLong(EVENT_ID_ORDER),
                            description = it.getString(EVENT_DESCRIPTION_ORDER),
                            day = dateParts[0],
                            month = dateParts[1],
                            year = dateParts[2],
                            disabled = it.getInt(EVENT_DISABLED_ORDER) == 1
                        ))
                        it.moveToNext()
                    }
                }
            }
        } catch (e: Exception) {
            LogUtilities.show(ApplicationDatabase::class.java, e)
        }
        return events
    }

    fun loadContacts(): List<ApplicationContact> {
        val contacts = mutableListOf<ApplicationContact>()
        try {
            val cursor = getContacts()
            cursor?.use {
                if (it.count != 0) {
                    it.moveToFirst()
                    while (!it.isAfterLast) {
                        val contact = ApplicationContact(
                            id = it.getLong(CONTACT_ID_ORDER),
                            name = it.getString(CONTACT_NAME_ORDER),
                            disabled = it.getInt(CONTACT_DISABLED_ORDER) == 1,
                            notes = it.getString(CONTACT_NOTES_ORDER)
                        )
                        for (event in loadEvents(contact.id)) {
                            contact.addEvent(event)
                        }
                        contacts.add(contact)
                        it.moveToNext()
                    }
                }
            }
        } catch (e: Exception) {
            LogUtilities.show(ApplicationDatabase::class.java, e)
        }
        return contacts
    }

    fun loadContact(id: Long): ApplicationContact? {
        var contact: ApplicationContact? = null
        if (id != ROWID_ERROR) {
            try {
                val cursor = getContact(id)
                cursor?.use {
                    if (it.count == 1) {
                        it.moveToFirst()
                        contact = ApplicationContact(
                            id = it.getLong(CONTACT_ID_ORDER),
                            name = it.getString(CONTACT_NAME_ORDER),
                            disabled = it.getInt(CONTACT_DISABLED_ORDER) == 1,
                            notes = it.getString(CONTACT_NOTES_ORDER)
                        )
                        for (event in loadEvents(contact.id)) {
                            contact.addEvent(event)
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtilities.show(ApplicationDatabase::class.java, e)
            }
        }
        return contact
    }

    fun setDeviceContactNotes(contactId: String, notes: String) {
        val values = ContentValues().apply {
            put(DEVICE_CONTACT_CONTACT_ID, contactId)
            put(DEVICE_CONTACT_NOTES, notes)
        }
        db.insertWithOnConflict(DEVICE_CONTACTS_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadDeviceContactsNotes(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val cursor = get(DEVICE_CONTACTS_TABLE_NAME, null, null, null, null)
            cursor?.use {
                while (it.moveToNext()) {
                    val contactId = it.getString(DEVICE_CONTACT_IMMUTABLE_CONTACT_ID_ORDER) ?: continue
                    map[contactId] = it.getString(DEVICE_CONTACT_NOTES_ORDER) ?: ""
                }
            }
        } catch (e: Exception) {
            LogUtilities.show(ApplicationDatabase::class.java, e)
        }
        return map
    }
}
