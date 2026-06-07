package com.ryosoftware.contact_dates_notifier.data.model

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import com.ryosoftware.contact_dates_notifier.data.local.ApplicationDatabase
import com.ryosoftware.utilities.LogUtilities
import androidx.core.graphics.drawable.toDrawable

class DeviceContact(
    val databaseContactIdentifier: Long,
    val immutableContactIdentifier: String?,
    name: String,
    accountName: String?,
    override var groups: List<String>? = null,
    override var notes: String? = null
) : Contact(name, accountName) {

    private var photo: Drawable? = null

    override fun equals(contact: Contact?): Boolean {
        return contact is DeviceContact &&
                immutableContactIdentifier == contact.immutableContactIdentifier
    }

    override fun getPhotoUri(context: Context): Uri? {
        val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, databaseContactIdentifier)
        val projection = arrayOf(ContactsContract.Contacts.PHOTO_URI)
        context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val uriStr = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI))
                if (uriStr != null) return Uri.parse(uriStr)
            }
        }
        return null
    }

    override fun getPhoto(context: Context): Drawable? {
        if (photo == null) {
            try {
                val inputStream = ContactsContract.Contacts.openContactPhotoInputStream(
                    context.contentResolver,
                    ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, databaseContactIdentifier),
                    true
                )
                if (inputStream != null) {
                    try {
                        photo = BitmapFactory.decodeStream(inputStream).toDrawable(context.resources)
                    } catch (e: Exception) {
                        LogUtilities.show(this, e)
                    } finally {
                        inputStream.close()
                    }
                }
            } catch (e: Exception) {
                LogUtilities.show(this, e)
            }
        }
        return photo
    }

    override fun setNotes(context: Context, notes: String?) {
        val trimmed = notes?.trim() ?: ""
        this.notes = trimmed
        val contactId = immutableContactIdentifier ?: return
        try {
            ApplicationDatabase.getInstance(context).setDeviceContactNotes(contactId, trimmed)
        } catch (e: Exception) {
            LogUtilities.show(this, e)
        }
    }
}
