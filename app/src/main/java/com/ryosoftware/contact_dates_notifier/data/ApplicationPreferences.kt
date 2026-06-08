package com.ryosoftware.contact_dates_notifier.data

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.DateUtils
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.preferencesDataStore
import com.ryosoftware.contact_dates_notifier.Main
import com.ryosoftware.contact_dates_notifier.R
import com.ryosoftware.contact_dates_notifier.data.local.ApplicationDatabase
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContactEvent
import com.ryosoftware.contact_dates_notifier.data.model.Contact
import com.ryosoftware.contact_dates_notifier.data.model.DeviceContact
import com.ryosoftware.utilities.DateTimeUtilities
import com.ryosoftware.utilities.LogUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.util.*

private class LegacySharedPreferencesMigration(private val context: Context) : DataMigration<Preferences> {
    private val prefs = context.getSharedPreferences(

        Main.instance?.packageName + "_preferences", Context.MODE_PRIVATE
    )

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        prefs.all.isNotEmpty()

    override suspend fun migrate(currentData: Preferences): Preferences {
        val mutable = mutablePreferencesOf()
        prefs.all.forEach { (key, value) ->
            when (value) {
                is String -> mutable[stringPreferencesKey(key)] = value
                is Int -> mutable[intPreferencesKey(key)] = value
                is Boolean -> mutable[booleanPreferencesKey(key)] = value
                is Long -> mutable[longPreferencesKey(key)] = value
                is Float -> mutable[floatPreferencesKey(key)] = value
                is Set<*> -> mutable[stringPreferencesKey(key)] = encodeStringSetStatic(value as Set<String>)
            }
        }
        return mutable
    }

    override suspend fun cleanUp() {
        prefs.edit().clear().apply()
    }

    private fun encodeStringSetStatic(values: Set<String>): String =
        JSONArray(values.toList()).toString()
}

private val Context.store: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(LegacySharedPreferencesMigration(context.applicationContext))
    }
)

object ApplicationPreferences {
    const val ENABLE_NOTIFICATIONS_KEY = "enable-notifications"
    var ENABLE_NOTIFICATIONS_DEFAULT = true

    const val NOT_NOTIFIED_ACCOUNTS_KEY = "not-notified-accounts"
    var NOT_NOTIFIED_ACCOUNTS_DEFAULT: Set<String>? = null
    const val APP_CONTACTS_ACCOUNT_NAME = ""
    const val LOCAL_CONTACTS_ACCOUNT_NAME = "\u0000"

    const val NOTIFICATION_DAYS_LEFT_KEY = "notification-days-left"
    var NOTIFICATION_DAYS_LEFT_DEFAULT = 7

    const val NOTIFICATION_HOUR_KEY = "notification-hour"
    var NOTIFICATION_HOUR_DEFAULT = "09:00"

    const val CLEARABLE_NOTIFICATION_KEY = "clearable-notification"
    var CLEARABLE_NOTIFICATION_DEFAULT = false

    const val CLEARABLE_NOTIFICATION_NOT_FOR_TODAY_EVENTS_KEY = "not-for-today-events"
    var CLEARABLE_NOTIFICATION_NOT_FOR_TODAY_EVENTS_DEFAULT = false

    const val DISABLED_BEHAVIOR_KEY = "disabled-behavior"
    const val DISABLED_BEHAVIOR_HIDE = "hide-disabled"
    const val DISABLED_BEHAVIOR_GRAY = "gray-disabled"
    const val DISABLED_BEHAVIOR_NONE = ""
    var DISABLED_BEHAVIOR_DEFAULT = DISABLED_BEHAVIOR_GRAY

    const val SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_HAS_EVENTS_IN_THE_NEAR_FUTURE_KEY = "show-large-photo-for-contacts-that-has-events-in-the-near-future"
    var SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_HAS_EVENTS_IN_THE_NEAR_FUTURE_DEFAULT = true

    const val SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_DO_NOT_HAS_EVENTS_IN_THE_NEAR_FUTURE_KEY = "show-large-photo-for-contacts-that-do-not-has-events-in-the-near-future"
    var SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_DO_NOT_HAS_EVENTS_IN_THE_NEAR_FUTURE_DEFAULT = false

    const val ADD_HEADER_FOR_TODAY_AND_TOMORROW_EVENTS_KEY = "add-header-for-today-and-tomorrow-events"
    var ADD_HEADER_FOR_TODAY_AND_TOMORROW_EVENTS_DEFAULT = true
    const val ADD_HEADER_FOR_YESTERDAY_TODAY_AND_TOMORROW_EVENTS_KEY = "add-header-for-yesterday-today-and-tomorrow-events"
    var ADD_HEADER_FOR_YESTERDAY_TODAY_AND_TOMORROW_EVENTS_DEFAULT = true

    const val GROUP_CONTACT_EVENTS_KEY = "group-contact-events"
    var GROUP_CONTACT_EVENTS_DEFAULT = true

    const val THEME_STYLE_KEY = "theme-style"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
    const val THEME_SYSTEM = "system"
    var THEME_STYLE_DEFAULT = THEME_SYSTEM

    const val PURE_BLACK_BACKGROUND_KEY = "pure-black-background"
    var PURE_BLACK_BACKGROUND_DEFAULT = false

    const val USE_SYSTEM_ACCENT_KEY = "use-system-accent"
    var USE_SYSTEM_ACCENT_DEFAULT = false

    const val CELEBRATION_LAST_DATE_KEY = "celebration-last-date"

    const val DATE_FORMAT_KEY = "date-format"
    const val DATE_FORMAT_SHORT = "short"
    const val DATE_FORMAT_MEDIUM = "medium"
    const val DATE_FORMAT_LONG = "long"
    var DATE_FORMAT_DEFAULT = DATE_FORMAT_MEDIUM

    @JvmStatic
    fun getDateFormatStyle(format: String): Int = when (format) {
        DATE_FORMAT_SHORT -> java.text.DateFormat.SHORT
        DATE_FORMAT_LONG -> java.text.DateFormat.LONG
        else -> java.text.DateFormat.MEDIUM
    }

    fun getDateFormatStyle(context: Context): Int =
        getDateFormatStyle(getString(context, DATE_FORMAT_KEY, DATE_FORMAT_DEFAULT) ?: DATE_FORMAT_DEFAULT)

    private const val VERSION_KEY = "version"
    private const val VERSION_VALUE = 2.5f

    private var iConstantsInitialized = false

    fun observeBoolean(context: Context, key: String, default: Boolean): Flow<Boolean> =
        context.store.data.map { it[booleanPreferencesKey(key)] ?: default }

    fun observeInt(context: Context, key: String, default: Int): Flow<Int> =
        context.store.data.map { it[intPreferencesKey(key)] ?: default }

    fun observeLong(context: Context, key: String, default: Long): Flow<Long> =
        context.store.data.map { it[longPreferencesKey(key)] ?: default }

    fun observeFloat(context: Context, key: String, default: Float): Flow<Float> =
        context.store.data.map { it[floatPreferencesKey(key)] ?: default }

    fun observeString(context: Context, key: String, default: String?): Flow<String?> =
        context.store.data.map { it[stringPreferencesKey(key)] ?: default }

    fun observeStringSet(context: Context, key: String, default: Set<String>?): Flow<Set<String>?> =
        context.store.data.map { decodeStringSet(it[stringPreferencesKey(key)]) ?: default }

    suspend fun setBoolean(context: Context, key: String, value: Boolean) {
        context.store.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun setInt(context: Context, key: String, value: Int) {
        context.store.edit { it[intPreferencesKey(key)] = value }
    }

    suspend fun setLong(context: Context, key: String, value: Long) {
        context.store.edit { it[longPreferencesKey(key)] = value }
    }

    suspend fun setFloat(context: Context, key: String, value: Float) {
        context.store.edit { it[floatPreferencesKey(key)] = value }
    }

    suspend fun setString(context: Context, key: String, value: String?) {
        context.store.edit {
            if (value != null) it[stringPreferencesKey(key)] = value
            else it.remove(stringPreferencesKey(key))
        }
    }

    suspend fun setStringSet(context: Context, key: String, value: Set<String>) {
        context.store.edit { it[stringPreferencesKey(key)] = encodeStringSet(value) }
    }

    fun removeKey(context: Context, key: String) =
        runBlocking { context.store.edit { it.remove(stringPreferencesKey(key)) } }

    fun getBoolean(context: Context, key: String, default: Boolean): Boolean =
        runBlocking { observeBoolean(context, key, default).first() }

    fun getInteger(context: Context, key: String, default: Int): Int =
        runBlocking { observeInt(context, key, default).first() }

    fun getLong(context: Context, key: String, default: Long): Long =
        runBlocking { observeLong(context, key, default).first() }

    fun getFloat(context: Context, key: String, default: Float): Float =
        runBlocking { observeFloat(context, key, default).first() }

    fun getString(context: Context, key: String, default: String?): String? =
        runBlocking { observeString(context, key, default).first() }

    fun getStrings(context: Context, key: String, default: Set<String>?): Set<String>? =
        runBlocking { observeStringSet(context, key, default).first() }

    fun putBoolean(context: Context, key: String, value: Boolean) =
        runBlocking { setBoolean(context, key, value) }

    fun putInteger(context: Context, key: String, value: Int) =
        runBlocking { setInt(context, key, value) }

    fun putLong(context: Context, key: String, value: Long) =
        runBlocking { setLong(context, key, value) }

    fun putFloat(context: Context, key: String, value: Float) =
        runBlocking { setFloat(context, key, value) }

    fun putString(context: Context, key: String, value: String?) =
        runBlocking { setString(context, key, value) }

    fun putStrings(context: Context, key: String, value: Set<String>) =
        runBlocking { setStringSet(context, key, value) }

    fun putStrings(context: Context, key: String, value: List<String>) =
        putStrings(context, key, value.toHashSet())

    fun hasKey(context: Context, key: String): Boolean =
        runBlocking { context.store.data.first().contains(stringPreferencesKey(key)) }

    private fun encodeStringSet(values: Set<String>): String =
        JSONArray(values.toList()).toString()

    private fun decodeStringSet(encoded: String?): Set<String>? {
        if (encoded == null) return null
        val json = JSONArray(encoded)
        return (0 until json.length()).map { json.getString(it) }.toSet()
    }

    object Contacts {
        private const val DO_NOT_DISPLAY_ALERTS_FROM_THIS_CONTACT_PREFIX = "do-not-display-alerts-from-this-contact"
        private const val DISABLED_EVENTS_PREFIX = "disabled-events-"
        const val CONTACT_BYPASSED_EVENTS_UNTIL_NEXT_YEAR_PREFIX = "contact-bypassed-events-until-next-year"
        const val CONTACT_BYPASSED_EVENTS_UNTIL_EVENT_DAY_PREFIX = "contact-bypassed-events-until-event-day"

        fun isDeviceContact(contact: Contact): Boolean = contact is DeviceContact

        private fun getContactKey(prefix: String, contactType: Char, id: String? = null): String {
            return if (id != null) "$prefix-$contactType-$id"
            else "$prefix-$contactType-"
        }

        private fun getContactKey(prefix: String, contact: Contact): String {
            return if (isDeviceContact(contact)) {
                val deviceContact = contact as DeviceContact
                val immutableId = deviceContact.immutableContactIdentifier
                if (immutableId == null) getContactKey(prefix, 'd', deviceContact.databaseContactIdentifier.toString())
                else getContactKey(prefix, 'D', immutableId)
            } else {
                getContactKey(prefix, 'a', (contact as ApplicationContact).id.toString())
            }
        }

        fun isDontDisplayAlertsFromThisContactEnabled(context: Context, contact: Contact): Boolean =
            getBoolean(context, getContactKey(DO_NOT_DISPLAY_ALERTS_FROM_THIS_CONTACT_PREFIX, contact), false)

        fun setDontDisplayAlertsFromThisContact(context: Context, contact: Contact, enabled: Boolean) {
            if (enabled) putBoolean(context, getContactKey(DO_NOT_DISPLAY_ALERTS_FROM_THIS_CONTACT_PREFIX, contact), true)
            else removeKey(context, getContactKey(DO_NOT_DISPLAY_ALERTS_FROM_THIS_CONTACT_PREFIX, contact))
        }

        private fun getEventKey(event: Contact.Event): String {
            val parts = DateTimeUtilities.getDateParts(event.time)
            val year = parts[0]
            val month = parts[1]
            val day = parts[2]
            return "$day-$month-$year:${event.description}"
        }

        fun isEventDisabled(context: Context, contact: Contact, event: Contact.Event): Boolean {
            if (contact is ApplicationContact && event is ApplicationContactEvent) {
                try {
                    val cursor = ApplicationDatabase.getInstance(context).getEvent(event.id)
                    cursor?.use {
                        if (it.count == 1) {
                            it.moveToFirst()
                            return it.getInt(ApplicationDatabase.EVENT_DISABLED_ORDER) == 1
                        }
                    }
                } catch (e: Exception) {
                    LogUtilities.show(ApplicationPreferences::class.java, e)
                }
                return false
            }
            val eventKey = getEventKey(event)
            val key = getContactKey(DISABLED_EVENTS_PREFIX, contact)
            val disabledEvents = getStrings(context, key, null) ?: return false
            return eventKey in disabledEvents
        }

        fun setEventDisabled(context: Context, contact: Contact, event: Contact.Event, disabled: Boolean) {
            if (contact is ApplicationContact && event is ApplicationContactEvent) {
                try {
                    ApplicationDatabase.getInstance(context).updateEventDisabled(event.id, disabled)
                } catch (e: Exception) {
                    LogUtilities.show(ApplicationPreferences::class.java, e)
                }
                return
            }
            val key = getContactKey(DISABLED_EVENTS_PREFIX, contact)
            val eventKey = getEventKey(event)
            val current = getStrings(context, key, null)?.toMutableSet() ?: mutableSetOf()
            if (disabled) current.add(eventKey)
            else current.remove(eventKey)
            if (current.isEmpty()) removeKey(context, key)
            else putStrings(context, key, current)
        }

        fun removeDisabledEvent(context: Context, contact: Contact, eventKey: String) {
            if (contact is ApplicationContact) {
                val parts = eventKey.split(":")
                if (parts.size == 2) {
                    val dateParts = parts[0].split("-")
                    if (dateParts.size >= 2) {
                        try {
                            val day = dateParts[0].toInt()
                            val month = dateParts[1].toInt()
                            val year = if (dateParts.size >= 3) dateParts[2].toInt() else Contact.Event.LEAP_YEAR
                            val description = parts[1]
                            ApplicationDatabase.getInstance(context).disableEventByKey(contact.id, day, month, year, description, false)
                        } catch (e: Exception) {
                            LogUtilities.show(ApplicationPreferences::class.java, e)
                        }
                    }
                }
                return
            }
            val key = getContactKey(DISABLED_EVENTS_PREFIX, contact)
            val current = getStrings(context, key, null)?.toMutableSet() ?: return
            if (current.remove(eventKey)) {
                if (current.isEmpty()) removeKey(context, key)
                else putStrings(context, key, current)
            }
        }

        fun getDisabledEvents(context: Context, contact: Contact): Set<String> {
            if (contact is ApplicationContact) {
                return try {
                    ApplicationDatabase.getInstance(context).getDisabledEventsKeys(contact.id)
                } catch (e: Exception) {
                    LogUtilities.show(ApplicationPreferences::class.java, e)
                    emptySet()
                }
            }
            val key = getContactKey(DISABLED_EVENTS_PREFIX, contact)
            return getStrings(context, key, null) ?: emptySet()
        }

        fun getContactKeyForBypassedUntilNextYearEvents(contact: Contact): String =
            getContactKey(CONTACT_BYPASSED_EVENTS_UNTIL_NEXT_YEAR_PREFIX, contact)

        private fun removeOlderEventsFromBypassedUntilNextYear(context: Context, contactKey: String) {
            val rawSet = getStrings(context, contactKey, null) ?: return
            val bypassedEvents = HashSet<String>(rawSet)
            val minTime = System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS * 400
            val iter = bypassedEvents.iterator()
            while (iter.hasNext()) {
                val value: String = iter.next()
                val parsedValue = value.split(":", limit = 2)
                if (parsedValue[0].toLong() < minTime) iter.remove()
            }
            if (bypassedEvents.isEmpty()) removeKey(context, contactKey)
            else putStrings(context, contactKey, bypassedEvents)
        }

        private fun isEventBypassedUntilNextYear(context: Context, contactKey: String, eventTime: Long, eventDescription: String?): Boolean {
            removeOlderEventsFromBypassedUntilNextYear(context, contactKey)
            val bypassedEvents = getStrings(context, contactKey, null) ?: return false
            val parsedValue = if (eventDescription == null) "$eventTime:" else "$eventTime:$eventDescription"
            return parsedValue in bypassedEvents
        }

        fun isEventBypassedUntilNextYear(context: Context, contact: Contact, eventTime: Long, eventDescription: String?): Boolean =
            isEventBypassedUntilNextYear(context, getContactKeyForBypassedUntilNextYearEvents(contact), eventTime, eventDescription)

        fun setEventBypassedUntilNextYear(context: Context, contactKey: String, eventTime: Long, eventDescription: String?) {
            removeOlderEventsFromBypassedUntilNextYear(context, contactKey)
            val bypassedEvents = getStrings(context, contactKey, null)?.toMutableSet() ?: mutableSetOf()
            val parsedValue = if (eventDescription == null) "$eventTime:" else "$eventTime:$eventDescription"
            if (parsedValue !in bypassedEvents) {
                bypassedEvents.add(parsedValue)
                putStrings(context, contactKey, bypassedEvents)
                LogUtilities.show(ApplicationPreferences::class.java, "Event at time ${DateTimeUtilities.getDateString(context, eventTime)} added to bypassed until next year events for contact identified by $contactKey")
            } else {
                LogUtilities.show(ApplicationPreferences::class.java, "Event at time ${DateTimeUtilities.getDateString(context, eventTime)} is already added to bypassed until next year events for contact identified by $contactKey")
            }
        }

        fun unsetEventBypassedUntilNextYear(context: Context, contactKey: String, eventTime: Long, eventDescription: String?) {
            removeOlderEventsFromBypassedUntilNextYear(context, contactKey)
            val bypassedEvents = getStrings(context, contactKey, null)?.toMutableSet() ?: return
            val parsedValue = if (eventDescription == null) "$eventTime:" else "$eventTime:$eventDescription"
            if (parsedValue in bypassedEvents) {
                bypassedEvents.remove(parsedValue)
                if (bypassedEvents.isEmpty()) removeKey(context, contactKey)
                else putStrings(context, contactKey, bypassedEvents)
            }
        }

        fun getContactKeyForBypassedUntilEventDayEvents(contact: Contact): String =
            getContactKey(CONTACT_BYPASSED_EVENTS_UNTIL_EVENT_DAY_PREFIX, contact)

        private fun removeOlderEventsFromBypassedUntilEventDay(context: Context, contactKey: String) {
            val rawSet = getStrings(context, contactKey, null) ?: return
            val bypassedEvents = HashSet<String>(rawSet)
            val minTime = System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS * 400
            val iterator = bypassedEvents.iterator()
            while (iterator.hasNext()) {
                val value: String = iterator.next()
                val parsedValue = value.split(":", limit = 2)
                if (parsedValue[0].toLong() < minTime) iterator.remove()
            }
            if (bypassedEvents.isEmpty()) removeKey(context, contactKey)
            else putStrings(context, contactKey, bypassedEvents)
        }

        private fun isEventBypassedUntilEventDay(context: Context, contactKey: String, eventTime: Long, eventDescription: String?): Boolean {
            removeOlderEventsFromBypassedUntilEventDay(context, contactKey)
            val bypassedEvents = getStrings(context, contactKey, null) ?: return false
            val parsedValue = if (eventDescription == null) "$eventTime:" else "$eventTime:$eventDescription"
            return parsedValue in bypassedEvents
        }

        fun isEventBypassedUntilEventDay(context: Context, contact: Contact, eventTime: Long, eventDescription: String?): Boolean =
            isEventBypassedUntilEventDay(context, getContactKeyForBypassedUntilEventDayEvents(contact), eventTime, eventDescription)

        fun setEventBypassedUntilEventDay(context: Context, contactKey: String, eventTime: Long, eventDescription: String?) {
            removeOlderEventsFromBypassedUntilEventDay(context, contactKey)
            var bypassedEvents = getStrings(context, contactKey, null)?.toMutableSet() ?: mutableSetOf()
            val parsedValue = if (eventDescription == null) "$eventTime:" else "$eventTime:$eventDescription"
            if (parsedValue !in bypassedEvents) {
                bypassedEvents.add(parsedValue)
                putStrings(context, contactKey, bypassedEvents)
                LogUtilities.show(ApplicationPreferences::class.java, "Event at time ${DateTimeUtilities.getDateString(context, eventTime)} added to bypassed until event day events for contact identified by $contactKey")
            } else {
                LogUtilities.show(ApplicationPreferences::class.java, "Event at time ${DateTimeUtilities.getDateString(context, eventTime)} is already added to bypassed until event day events for contact identified by $contactKey")
            }
        }
    }

    @SuppressLint("CommitPrefEdits")
    private suspend fun upgrade(context: Context, from: Float) {
        val store = context.store
        store.edit { prefs ->
            if (from < 2.1f) prefs.remove(stringPreferencesKey("cookies-consent-obtained"))
            if (from < 2.2f) {
                val keys = prefs.asMap().keys.map { it.name }
                val oldPrefix = "contact-bypassed-events-"
                for (key in keys) {
                    if (!key.startsWith(Contacts.CONTACT_BYPASSED_EVENTS_UNTIL_NEXT_YEAR_PREFIX) &&
                        !key.startsWith(Contacts.CONTACT_BYPASSED_EVENTS_UNTIL_EVENT_DAY_PREFIX) &&
                        key.startsWith(oldPrefix)) {
                        val oldKey = stringPreferencesKey(key)
                        val value = prefs[oldKey]
                        if (value != null) {
                            prefs[stringPreferencesKey("${Contacts.CONTACT_BYPASSED_EVENTS_UNTIL_NEXT_YEAR_PREFIX}-${key.substring(oldPrefix.length)}")] = value
                            prefs.remove(oldKey)
                        }
                    }
                }
            }
            if (from < 2.4f) {
                val notifiedAccountsKey = stringPreferencesKey("notified_accounts")
                val notifiedAccounts = prefs[notifiedAccountsKey]
                if (notifiedAccounts != null) {
                    prefs.remove(notifiedAccountsKey)
                    val oldNotified = decodeStringSet(notifiedAccounts) ?: emptySet()
                    val accountsNames = DeviceContactsDriver.getAccounts(context)
                    val notNotified = mutableSetOf<String>()
                    for (accountName in accountsNames) if (accountName !in oldNotified) notNotified.add(accountName)
                    if (APP_CONTACTS_ACCOUNT_NAME !in oldNotified) notNotified.add(APP_CONTACTS_ACCOUNT_NAME)
                    if (notNotified.isNotEmpty()) {
                        prefs[stringPreferencesKey(NOT_NOTIFIED_ACCOUNTS_KEY)] = encodeStringSet(notNotified)
                    }
                }
            }
            if (from < 2.5f) prefs.remove(stringPreferencesKey("cookies-consent-obtained"))
        }
    }

    private suspend fun initializeConstants(context: Context) {
        if (!iConstantsInitialized) {
            ENABLE_NOTIFICATIONS_DEFAULT = context.getString(R.string.enable_notifications_default).toBoolean()
            NOTIFICATION_DAYS_LEFT_DEFAULT = context.getString(R.string.notification_days_left_default).toInt()
            NOTIFICATION_HOUR_DEFAULT = context.getString(R.string.notification_hour_default)
            CLEARABLE_NOTIFICATION_DEFAULT = context.getString(R.string.clearable_notification_default).toBoolean()
            CLEARABLE_NOTIFICATION_NOT_FOR_TODAY_EVENTS_DEFAULT = context.getString(R.string.clearable_notification_not_for_today_events_default).toBoolean()
            DISABLED_BEHAVIOR_DEFAULT = context.getString(R.string.disabled_behavior_default)
            SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_HAS_EVENTS_IN_THE_NEAR_FUTURE_DEFAULT = context.getString(R.string.show_large_photo_for_contacts_that_has_events_in_the_near_future_default).toBoolean()
            SHOW_LARGE_PHOTO_FOR_CONTACTS_THAT_DO_NOT_HAS_EVENTS_IN_THE_NEAR_FUTURE_DEFAULT = context.getString(R.string.show_large_photo_for_contacts_that_do_not_has_events_in_the_near_future_default).toBoolean()
            ADD_HEADER_FOR_TODAY_AND_TOMORROW_EVENTS_DEFAULT = context.getString(R.string.add_header_for_today_and_tomorrow_events_default).toBoolean()
            GROUP_CONTACT_EVENTS_DEFAULT = context.getString(R.string.group_contact_events_default).toBoolean()
            THEME_STYLE_DEFAULT = context.getString(R.string.theme_style_default)
            PURE_BLACK_BACKGROUND_DEFAULT = context.getString(R.string.pure_black_background_default).toBoolean()
            USE_SYSTEM_ACCENT_DEFAULT = context.getString(R.string.use_system_accent_default).toBoolean()
            DATE_FORMAT_DEFAULT = context.getString(R.string.date_format_default)
            iConstantsInitialized = true
        }
    }

    fun initialize(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            initializeConstants(context)
            val version = getFloat(context, VERSION_KEY, 0f)
            if (version != VERSION_VALUE) {
                if (version < VERSION_VALUE) upgrade(context, version)
                putFloat(context, VERSION_KEY, VERSION_VALUE)
            }
        }
    }
}
