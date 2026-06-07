package com.ryosoftware.contact_dates_notifier.data.model

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import com.ryosoftware.utilities.DateTimeUtilities
import com.ryosoftware.utilities.LogUtilities
import com.ryosoftware.contact_dates_notifier.data.local.ApplicationDatabase
import java.io.File
import java.io.FileInputStream
import androidx.core.graphics.drawable.toDrawable

class ApplicationContactEvent(
    var id: Long = -1,
    description: String,
    day: Int,
    month: Int,
    year: Int,
    var disabled: Boolean = false
) : Contact.Event(description, day, month, if (year == 0) null else year) {
    var descriptionChanged = true
    var timeChanged = true

    val day: Int
        get() = DateTimeUtilities.getDateParts(time)[2]

    val month: Int
        get() = DateTimeUtilities.getDateParts(time)[1]

    val year: Int
        get() = DateTimeUtilities.getDateParts(time)[0]

}

class ApplicationContact @JvmOverloads constructor(
    var id: Long = -1,
    name: String? = null,
    var disabled: Boolean = false,
    override var notes: String? = ""
) : Contact(name, null) {

    private var nameChanged = false
    private var currentPhoto: Drawable? = null
    var newPhoto: File? = null

    override var name: String?
        get() = super.name
        set(value) {
            val oldName = super.name
            nameChanged = if (oldName == null) value != null else oldName != value
            super.name = value
        }

    override fun equals(contact: Contact?): Boolean {
        return contact is ApplicationContact && id != -1L && id == contact.id
    }

    override fun getPhotoUri(context: Context): Uri? {
        val file = getPhotoFilename(context) ?: return null
        if (!file.exists()) return null
        return Uri.fromFile(file)
    }

    override fun getPhoto(context: Context): Drawable? {
        if (currentPhoto == null) {
            val file = getPhotoFilename(context)
            if (file != null && file.exists()) {
                try {
                    currentPhoto = FileInputStream(file).use { inputStream ->
                        BitmapFactory.decodeStream(inputStream).toDrawable(context.resources)
                    }
                } catch (e: Exception) {
                    LogUtilities.show(this, e)
                }
            }
        }
        return currentPhoto
    }

    fun addAppEvent(description: String, day: Int, month: Int, year: Int, disabled: Boolean = false) {
        addEvent(ApplicationContactEvent(description = description, day = day, month = month, year = year, disabled = disabled))
    }

    fun removeAppEvent(event: ApplicationContactEvent) {
        val toRemove = events.find { it.description == event.description && it.time == event.time }
        toRemove?.let { deleteDate(it) }
    }

    private fun getPhotoFilename(context: Context): File? {
        if (id == -1L) return null
        return File(context.getExternalFilesDir("contacts"), id.toString())
    }

    fun update(context: Context): Boolean {
        return try {
            val db = ApplicationDatabase.getInstance(context)
            db.beginTransaction()
            try {
                if (id == -1L) {
                    id = db.addContact(name ?: "", notes = notes ?: "")
                    if (id == -1L) throw Exception("Can't add contact '$name' to the contacts database")
                } else {
                    if (nameChanged && !db.updateContact(id, name ?: "")) throw Exception("Can't update contact identified by $id")
                    if (!db.updateContactNotes(id, notes ?: "")) throw Exception("Can't update contact notes")
                    if (!db.updateContactDisabled(id, disabled)) throw Exception("Can't update contact disabled state")
                    db.deleteEvents(id)
                }
                for (i in events.indices.reversed()) {
                    val event = events[i] as ApplicationContactEvent
                    val parts = DateTimeUtilities.getDateParts(event.time)
                    db.addEvent(id, parts[2], parts[1], parts[0], event.description, event.disabled)
                }
                if (newPhoto != null) {
                    val dest = getPhotoFilename(context)
                    if (dest != null) {
                        try {
                            FileInputStream(newPhoto!!).use { src ->
                                dest.outputStream().use { dst -> src.copyTo(dst) }
                            }
                        } catch (e: Exception) {
                            LogUtilities.show(this, e)
                        }
                    }
                }
                db.commitTransaction()
                nameChanged = false
                newPhoto = null
                true
            } catch (e: Exception) {
                LogUtilities.show(this, e)
                false
            }
        } catch (e: Exception) {
            LogUtilities.show(this, e)
            false
        }
    }

    fun delete(context: Context): Boolean {
        if (id != -1L) {
            return try {
                val db = ApplicationDatabase.getInstance(context)
                db.beginTransaction()
                try {
                    if (db.deleteEvents(id) < 0) throw Exception("Can't delete contact events")
                    if (!db.deleteContact(id)) throw Exception("Can't delete contact")
                    getPhotoFilename(context)?.delete()
                    db.commitTransaction()
                    id = -1
                    newPhoto = null
                    true
                } catch (e: Exception) {
                    LogUtilities.show(this, e)
                    false
                }
            } catch (e: Exception) {
                LogUtilities.show(this, e)
                false
            }
        }
        return true
    }

    fun setDisabled(context: Context, disabled: Boolean): Boolean {
        if (id == -1L) return false
        return try {
            ApplicationDatabase.getInstance(context).updateContactDisabled(id, disabled)
        } catch (e: Exception) {
            LogUtilities.show(this, e)
            false
        }
    }

    override fun setNotes(context: Context, notes: String?) {
        val trimmed = notes?.trim() ?: ""
        this.notes = trimmed
        if (id == -1L) return
        try {
            ApplicationDatabase.getInstance(context).updateContactNotes(id, trimmed)
        } catch (e: Exception) {
            LogUtilities.show(this, e)
        }
    }
}
