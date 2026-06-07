package com.ryosoftware.contact_dates_notifier.data.model

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.ryosoftware.utilities.DateTimeUtilities
import java.util.*

abstract class Contact(
    open var name: String?,
    val accountName: String?
) {
    open var notes: String? = null
    open val groups: List<String>? get() = null

    abstract fun setNotes(context: Context, notes: String?)
    abstract fun getPhotoUri(context: Context): android.net.Uri?

    companion object {
        private val COLORS = intArrayOf(
            0xFFF38630.toInt(), 0xFFFF6161.toInt(), 0xFF69D2E7.toInt(),
            0xFFFE4365.toInt(), 0xFF83AF9B.toInt()
        )
        private val randomizer = Random()
        private val accountsColorsCache = LruCache<String, Int?>(64)
        private val eventsComparator = Comparator<Event> { left, right ->
            val leftTime = left.nextIteration
            val rightTime = right.nextIteration
            when {
                leftTime < rightTime -> -1
                leftTime > rightTime -> 1
                else -> 0
            }
        }
    }

    open class Event(
        description: String,
        day: Int,
        month: Int,
        year: Int? = null
    ) {
        companion object {
            const val LEAP_YEAR = 1904
        }

        var description: String = description
        var time: Long = if (year != null) DateTimeUtilities.getDateFromParts(year, month, day)
        else DateTimeUtilities.getDateFromParts(LEAP_YEAR, month, day)
            internal set
        @get:JvmName("hasYear")
    val hasYear: Boolean = year != null

        constructor(description: String, parts: IntArray) : this(
            description = description,
            day = parts[0],
            month = parts[1],
            year = if (parts.size == 3) parts[2] else null
        )

        val yearsUntilNextIteration: Int
            get() {
                val startDate = DateTimeUtilities.getCalendar(time)
                val nextDate = DateTimeUtilities.getCalendar(nextIteration)
                return nextDate[Calendar.YEAR] - startDate[Calendar.YEAR]
            }

        val nextIteration: Long
            get() = DateTimeUtilities.setToFuturePreservingDayAndMonth(time)

        @JvmOverloads
        fun daysUntilNextIteration(now: Long = System.currentTimeMillis()): Int =
            DateTimeUtilities.getDays(now, nextIteration)
    }

    val events = mutableListOf<Event>()

    var birthEvent: Contact.Event? = null

    fun addEvent(event: Event) {
        events.add(event)
        events.sortWith(eventsComparator)
    }

    fun deleteDate(event: Event) {
        events.remove(event)
    }

    val eventsCount: Int get() = events.size

    fun getEvent(index: Int): Event = events[index]

    val firstEvent: Event? get() = events.firstOrNull()

    abstract fun equals(contact: Contact?): Boolean

    abstract fun getPhoto(context: Context): Drawable?
}
