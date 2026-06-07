package com.ryosoftware.contact_dates_notifier.data.repository

import android.content.Context
import com.ryosoftware.contact_dates_notifier.data.ApplicationContactsDriver
import com.ryosoftware.contact_dates_notifier.data.DeviceContactsDriver
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import com.ryosoftware.contact_dates_notifier.data.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {

    suspend fun getAllContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val deviceContacts = DeviceContactsDriver.get(context)
        val appContacts = ApplicationContactsDriver.getContacts(context)
        (deviceContacts + appContacts).sortedBy { it.name?.lowercase() }
    }

    suspend fun getDeviceContacts(): List<Contact> = withContext(Dispatchers.IO) {
        DeviceContactsDriver.get(context)
    }

    suspend fun getAppContacts(): List<Contact> = withContext(Dispatchers.IO) {
        ApplicationContactsDriver.getContacts(context)
    }

    suspend fun getAppContact(id: Long): ApplicationContact? = withContext(Dispatchers.IO) {
        ApplicationContactsDriver.getContact(context, id)
    }

    suspend fun getAccounts(): List<String> = withContext(Dispatchers.IO) {
        DeviceContactsDriver.getAccounts(context)
    }
}
