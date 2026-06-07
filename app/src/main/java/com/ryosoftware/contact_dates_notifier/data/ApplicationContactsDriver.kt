package com.ryosoftware.contact_dates_notifier.data

import android.content.Context
import com.ryosoftware.contact_dates_notifier.data.local.ApplicationDatabase
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import com.ryosoftware.contact_dates_notifier.data.model.Contact

object ApplicationContactsDriver {
    const val ROWID_ERROR = -1L

    fun getContacts(context: Context): List<Contact> {
        return try {
            ApplicationDatabase.getInstance(context).loadContacts()
        } catch (e: Exception) {
            com.ryosoftware.utilities.LogUtilities.show(ApplicationContactsDriver::class.java, e)
            emptyList()
        }
    }

    fun get(context: Context): List<Contact> = getContacts(context)

    fun get(context: Context, id: Long): ApplicationContact? = getContact(context, id)

    fun getContact(context: Context, id: Long): ApplicationContact? {
        if (id == ROWID_ERROR) return null
        return try {
            ApplicationDatabase.getInstance(context).loadContact(id)
        } catch (e: Exception) {
            com.ryosoftware.utilities.LogUtilities.show(ApplicationContactsDriver::class.java, e)
            null
        }
    }
}
