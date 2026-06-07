package com.ryosoftware.utilities

import android.content.Context
import android.os.SystemClock
import android.text.format.DateFormat
import android.text.format.DateUtils
import com.ryosoftware.contact_dates_notifier.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

object DateTimeUtilities {
    private const val UTC_TIMEZONE = "UTC"

    private var iDayOfWeekFormat = SimpleDateFormat("EE")

    private fun getCurrentTime() = System.currentTimeMillis()

    fun toMidnightTime(calendar: Calendar): Long {
        calendar[Calendar.HOUR_OF_DAY] = 0
        calendar[Calendar.MINUTE] = 0
        calendar[Calendar.SECOND] = 0
        calendar[Calendar.MILLISECOND] = 0
        return calendar.timeInMillis
    }

    fun toMidnightTime(time: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        return toMidnightTime(calendar)
    }

    fun getCalendar(time: Long): Calendar {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        return calendar
    }

    fun getCalendar() = getCalendar(getCurrentTime())

    private fun hasChangedTimeFromWinterToSummerSinceMidnight(time: Long): Boolean {
        val calendar = getCalendar(time)
        val midnightTime = toMidnightTime(time)
        val timeSinceMidnight = calendar[Calendar.HOUR_OF_DAY].toLong() * DateUtils.HOUR_IN_MILLIS +
                calendar[Calendar.MINUTE].toLong() * DateUtils.MINUTE_IN_MILLIS +
                calendar[Calendar.SECOND].toLong() * DateUtils.SECOND_IN_MILLIS +
                calendar[Calendar.MILLISECOND]
        return (time - midnightTime - timeSinceMidnight < 0)
    }

    private fun hasChangedTimeFromSummerToWinterSinceMidnight(time: Long): Boolean {
        val calendar = getCalendar(time)
        val midnightTime = toMidnightTime(time)
        val timeSinceMidnight = calendar[Calendar.HOUR_OF_DAY].toLong() * DateUtils.HOUR_IN_MILLIS +
                calendar[Calendar.MINUTE].toLong() * DateUtils.MINUTE_IN_MILLIS +
                calendar[Calendar.SECOND].toLong() * DateUtils.SECOND_IN_MILLIS +
                calendar[Calendar.MILLISECOND]
        return (time - midnightTime - timeSinceMidnight > 0)
    }

    fun getDays(start: Long, end: Long): Int {
        var start = start
        var end = end
        val hasChangedTimeFromWinterToSummer = hasChangedTimeFromWinterToSummerSinceMidnight(start)
        start = toMidnightTime(start)
        end = toMidnightTime(end)
        var difference = (end - start)
        var days = (difference / DateUtils.DAY_IN_MILLIS).toInt()
        if (hasChangedTimeFromWinterToSummer) {
            difference -= days * DateUtils.DAY_IN_MILLIS
            if (difference != 0L) days += if (difference > 0) 1 else -1
        }
        return days
    }

    fun getDaysUntilToday(start: Long) = getDays(start, System.currentTimeMillis())

    fun getRelativeDays(time: Long, now: Long = System.currentTimeMillis()): Int {
        val nowMidnight = toMidnightTime(now)
        val date = getCalendar(toMidnightTime(time))
        val nowDate = getCalendar(nowMidnight)
        date[Calendar.YEAR] = nowDate[Calendar.YEAR]
        return getDays(nowMidnight, date.timeInMillis)
    }

    fun getTimeFromMidnight(time: Long) = time - toMidnightTime(time)

    fun refersToSameDay(time1: Long, time2: Long): Boolean {
        val calendar1 = getCalendar(time1)
        val calendar2 = getCalendar(time2)
        if (calendar1[Calendar.YEAR] != calendar2[Calendar.YEAR]) return false
        if (calendar1[Calendar.MONTH] != calendar2[Calendar.MONTH]) return false
        if (calendar1[Calendar.DAY_OF_MONTH] != calendar2[Calendar.DAY_OF_MONTH]) return false
        return true
    }

    fun setToFuturePreservingDayOfWeek(time: Long, now: Long): Long {
        var time = time
        var now = now
        time = toMidnightTime(time)
        now = toMidnightTime(if (now == 0L) getCurrentTime() else now)
        if (time < now) {
            val date = getCalendar(time)
            val nowDate = getCalendar(now)
            var searchedDayOfWeek = date[Calendar.DAY_OF_WEEK]
            val nowDayOfWeek = nowDate[Calendar.DAY_OF_WEEK]
            if (searchedDayOfWeek < nowDayOfWeek) {
                searchedDayOfWeek += 7
            }
            time = nowDate.timeInMillis + (searchedDayOfWeek - nowDayOfWeek) * DateUtils.DAY_IN_MILLIS
        }
        return toMidnightTime(time)
    }

    fun setToFuturePreservingDayOfWeek(time: Long) = setToFuturePreservingDayOfWeek(time, 0)

    fun setToFuturePreservingDayOfMonth(time: Long, now: Long): Long {
        var time = time
        var now = now
        time = toMidnightTime(time)
        now = toMidnightTime(if (now == 0L) getCurrentTime() else now)
        if (time < now) {
            val date = getCalendar(time)
            val nowDate = getCalendar(now)
            date[Calendar.YEAR] = nowDate[Calendar.YEAR]
            date[Calendar.MONTH] = nowDate[Calendar.MONTH]
            if (date.timeInMillis < now) {
                var month = date[Calendar.MONTH] + 1
                date[Calendar.MONTH] = month
                if (date[Calendar.MONTH] != month % 12) {
                    date[Calendar.DAY_OF_MONTH] = 1
                    date[Calendar.YEAR] = nowDate[Calendar.YEAR]
                    date[Calendar.MONTH] = month
                    date[Calendar.DAY_OF_MONTH] = date.getActualMaximum(Calendar.DAY_OF_MONTH)
                }
            } else if (date[Calendar.MONTH] != nowDate[Calendar.MONTH]) {
                date[Calendar.DAY_OF_MONTH] = 1
                date[Calendar.YEAR] = nowDate[Calendar.YEAR]
                date[Calendar.MONTH] = nowDate[Calendar.MONTH]
                date[Calendar.DAY_OF_MONTH] = date.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
            time = date.timeInMillis
        }
        return toMidnightTime(time)
    }

    fun setToFuturePreservingDayOfMonth(time: Long) = setToFuturePreservingDayOfMonth(time, 0)

    fun setToFuturePreservingDayAndMonth(time: Long, now: Long): Long {
        var time = time
        var now = now
        time = toMidnightTime(time)
        now = toMidnightTime(if (now == 0L) getCurrentTime() else now)
        if (time < now) {
            val date = getCalendar(time)
            val nowDate = getCalendar(now)
            date[Calendar.YEAR] = nowDate[Calendar.YEAR]
            if (date.timeInMillis < now) date[Calendar.YEAR] = date[Calendar.YEAR] + 1
            time = date.timeInMillis
        }
        return toMidnightTime(time)
    }

    fun setToFuturePreservingDayAndMonth(time: Long) = setToFuturePreservingDayAndMonth(time, 0)

    fun getDateParts(time: Long): IntArray {
        val date = getCalendar(if (time == 0L) getCurrentTime() else time)
        return intArrayOf(date[Calendar.YEAR], date[Calendar.MONTH] + 1, date[Calendar.DAY_OF_MONTH])
    }

    fun getDateFromParts(year: Int, month: Int, day: Int): Long {
        val date = GregorianCalendar()
        date[Calendar.YEAR] = year
        date[Calendar.MONTH] = month - 1
        date[Calendar.DAY_OF_MONTH] = day
        return date.timeInMillis
    }

    private fun extractYearFromDateFormat(dateFormat: SimpleDateFormat) {
        try {
            dateFormat.applyPattern(dateFormat.toPattern().replace("[^\\p{Alpha}]*y+[^\\p{Alpha}]*".toRegex(), ""))
        } catch (_: Exception) {
        }
    }

    fun getDateStringWithoutYear(context: Context, time: Long): String {
        val dateFormat = SimpleDateFormat.getDateInstance() as SimpleDateFormat
        extractYearFromDateFormat(dateFormat)
        return dateFormat.format(time)
    }

    fun getDateString(context: Context, calendar: Calendar, showYearOnlyIfNeeded: Boolean): String {
        val dateFormat = SimpleDateFormat.getDateInstance() as SimpleDateFormat
        if (showYearOnlyIfNeeded) {
            val yearNeedsToBeShown: Boolean
            val today = getCalendar()
            val requestedDateYear = calendar[Calendar.YEAR]
            val currentDateYear = today[Calendar.YEAR]
            if (requestedDateYear + 1 == currentDateYear) {
                val requestedDateMonth = calendar[Calendar.MONTH]
                val currentDateMonth = today[Calendar.MONTH]
                yearNeedsToBeShown = ((requestedDateMonth < currentDateMonth) ||
                        ((requestedDateMonth == currentDateMonth) && (calendar[Calendar.DAY_OF_MONTH] <= today[Calendar.DAY_OF_MONTH])))
            } else yearNeedsToBeShown = (requestedDateYear != currentDateYear)
            if (!yearNeedsToBeShown) extractYearFromDateFormat(dateFormat)
        }
        return dateFormat.format(calendar.time)
    }

    fun getDateString(context: Context, time: Long, showYearOnlyIfNeeded: Boolean) =
            getDateString(context, getCalendar(if (time == 0L) getCurrentTime() else time), showYearOnlyIfNeeded)

    fun getDateString(context: Context, time: Long) = getDateString(context, time, false)

    fun getDateString(formatStyle: Int, time: Long): String {
        val dateFormat = SimpleDateFormat.getDateInstance(formatStyle)
        return dateFormat.format(time)
    }

    fun getDateString(formatStyle: Int, year: Int, month: Int, day: Int): String {
        return getDateString(formatStyle, getDateFromParts(year, month, day))
    }

    fun getDateStringWithoutYear(formatStyle: Int, time: Long): String {
        val dateFormat = SimpleDateFormat.getDateInstance(formatStyle) as SimpleDateFormat
        extractYearFromDateFormat(dateFormat)
        return dateFormat.format(time)
    }

    fun getDateStringWithoutYear(formatStyle: Int, year: Int, month: Int, day: Int): String {
        return getDateStringWithoutYear(formatStyle, getDateFromParts(year, month, day))
    }

    fun getTimeString(context: Context, calendar: Calendar, showSeconds: Boolean) =
            if (showSeconds) SimpleDateFormat.getTimeInstance(SimpleDateFormat.MEDIUM).format(calendar.time)
            else DateFormat.getTimeFormat(context).format(calendar.time)

    fun getTimeString(context: Context, time: Long, showSeconds: Boolean) =
            getTimeString(context, getCalendar(if (time == 0L) getCurrentTime() else time), showSeconds)

    fun getTimeString(format: String, `when`: Long) =
            DateFormat.format(format, getCalendar(`when`).time).toString()

    fun getTimeString(`when`: Long) = getTimeString("dd/MM/yy kk:mm:ss", `when`)

    fun getTimeString(context: Context, resource: Int, `when`: Long) =
            DateFormat.format(context.getString(resource), getCalendar(`when`).time).toString()

    fun getNowString() = getTimeString(getCurrentTime())

    fun getDateTimeString(context: Context, resource: Int, time: Long, showSeconds: Boolean,
                          showYearOnlyIfNeeded: Boolean, resourceIsFromPluralsInsteadOfFromStrings: Boolean): String {
        val calendar = getCalendar(if (time == 0L) getCurrentTime() else time)
        val dateString = getDateString(context, calendar, showYearOnlyIfNeeded)
        val timeString = getTimeString(context, calendar, showSeconds)
        if (resource == 0) return String.format("%s at %s", dateString, timeString)
        if (resourceIsFromPluralsInsteadOfFromStrings) return context.resources.getQuantityString(
                resource, calendar[Calendar.HOUR_OF_DAY], dateString, timeString)
        return context.getString(resource, dateString, timeString)
    }

    fun getDateTimeString(context: Context, time: Long, showSeconds: Boolean, showYearOnlyIfNeeded: Boolean) =
            getDateTimeString(context, 0, time, showSeconds, showYearOnlyIfNeeded, false)

    fun getLocaleHour(context: Context, hour: String): String {
        val date = getCalendar(getCurrentTime())
        val data = hour.split(":")
        date[Calendar.HOUR_OF_DAY] = data[0].toInt()
        date[Calendar.MINUTE] = data[1].toInt()
        return DateFormat.getTimeFormat(context).format(date.time)
    }

    fun getDayTimeHour(hour: String): Long {
        val data = hour.split(":")
        return data[0].toLong() * DateUtils.HOUR_IN_MILLIS + data[1].toLong() * DateUtils.MINUTE_IN_MILLIS
    }

    fun getCurrentDayTimeHour(hour: String): Long {
        val data = hour.split(":")
        val now = getCurrentTime()
        return toMidnightTime(now) + data[0].toLong() * DateUtils.HOUR_IN_MILLIS + data[1].toLong() * DateUtils.MINUTE_IN_MILLIS
    }

    fun getNextDayTimeHour(hour: String): Long {
        val data = hour.split(":")
        val now = getCurrentTime()
        var time = toMidnightTime(now) + data[0].toLong() * DateUtils.HOUR_IN_MILLIS + data[1].toLong() * DateUtils.MINUTE_IN_MILLIS
        if (time < now) time += DateUtils.DAY_IN_MILLIS
        return time
    }

    fun convert(time: Long, fromTimeZoneString: String?, toTimeZoneString: String?): Long {
        var time = time
        if ((fromTimeZoneString != null) && (toTimeZoneString != null)) {
            val fromTimeZone = TimeZone.getTimeZone(fromTimeZoneString)
            val toTimeZone = TimeZone.getTimeZone(toTimeZoneString)
            if (!fromTimeZone.hasSameRules(toTimeZone)) {
                val calendar = Calendar.getInstance()
                calendar.timeZone = fromTimeZone
                calendar.timeInMillis = time
                if (!fromTimeZone.hasSameRules(TimeZone.getTimeZone("UTC"))) {
                    calendar.add(Calendar.MILLISECOND, fromTimeZone.rawOffset * -1)
                    if (fromTimeZone.inDaylightTime(calendar.time)) calendar.add(Calendar.MILLISECOND, fromTimeZone.dstSavings * -1)
                }
                if (!toTimeZone.hasSameRules(TimeZone.getTimeZone("UTC"))) {
                    calendar.add(Calendar.MILLISECOND, toTimeZone.rawOffset)
                    if (toTimeZone.inDaylightTime(calendar.time)) calendar.add(Calendar.MILLISECOND, toTimeZone.dstSavings)
                }
                time = calendar.timeInMillis
            }
        }
        return time
    }

    fun getLocalizedTimeZone() = Calendar.getInstance().timeZone.id

    fun convertFrom(time: Long, fromTimeZoneString: String) =
            convert(time, fromTimeZoneString, getLocalizedTimeZone())

    fun convertFromUTC(time: Long) = convert(time, UTC_TIMEZONE, getLocalizedTimeZone())

    fun convertTo(time: Long, toTimeZoneString: String) =
            convert(time, getLocalizedTimeZone(), toTimeZoneString)

    fun convertToUTC(time: Long) = convert(time, getLocalizedTimeZone(), UTC_TIMEZONE)

    fun getWeekDay(time: Long): Int {
        val calendar = getCalendar(time)
        return calendar[Calendar.DAY_OF_WEEK]
    }

    fun getDayHourTime(time: Long): Long {
        val calendar = getCalendar(time)
        return calendar[Calendar.HOUR_OF_DAY].toLong() * DateUtils.HOUR_IN_MILLIS +
                calendar[Calendar.MINUTE].toLong() * DateUtils.MINUTE_IN_MILLIS +
                calendar[Calendar.SECOND].toLong() * DateUtils.SECOND_IN_MILLIS
    }

    fun getStringTimeFromDayHourTime(context: Context, time: Long): String {
        var time = time
        val hour = time / DateUtils.HOUR_IN_MILLIS
        time -= (hour * DateUtils.HOUR_IN_MILLIS)
        val minutes = time / DateUtils.MINUTE_IN_MILLIS
        return context.getString(R.string.hour_value, hour, minutes)
    }

    fun inRange(weekDay: Int, dayTime: Long, startWeekDay: Int, startTime: Long,
                endWeekDay: Int, endTime: Long): Boolean {
        if (startWeekDay == endWeekDay) {
            if (startTime < endTime) {
                if (startWeekDay == weekDay) return ((startTime <= dayTime) && (dayTime <= endTime))
                return false
            } else {
                if (startWeekDay == weekDay) return ((startTime <= dayTime) || (dayTime <= endTime))
                return true
            }
        } else if (startWeekDay < endWeekDay) {
            if (startWeekDay == weekDay) return (startTime <= dayTime)
            if (endWeekDay == weekDay) return (dayTime <= endTime)
            return ((startWeekDay < weekDay) && (weekDay < endWeekDay))
        } else {
            if (startWeekDay == weekDay) return (startTime <= dayTime)
            if (endWeekDay == weekDay) return (dayTime <= endTime)
            return ((startWeekDay < weekDay) || (weekDay < endWeekDay))
        }
    }

    fun toMinuteStart(time: Long, nextMinuteSecond: Long): Long {
        var time = time
        var nextMinuteSecond = nextMinuteSecond
        nextMinuteSecond *= DateUtils.SECOND_IN_MILLIS
        if ((nextMinuteSecond > 0) && (time % DateUtils.MINUTE_IN_MILLIS >= nextMinuteSecond)) time += DateUtils.MINUTE_IN_MILLIS
        return (time / DateUtils.MINUTE_IN_MILLIS) * DateUtils.MINUTE_IN_MILLIS
    }

    fun toMinuteStart(time: Long) = toMinuteStart(time, 0)

    fun toNextMinuteStart(time: Long): Long {
        val nextMinute = toMinuteStart(time)
        return if (nextMinute == time) time + DateUtils.MINUTE_IN_MILLIS else nextMinute
    }

    fun julianDayToTime(julianDay: Long): Long {
        val z: Double
        val f: Double
        val a: Double
        val b: Double
        val c: Double
        val d: Double
        val e: Double
        val m: Double
        val aux: Double
        val date = Date()
        var julian_day = julianDay.toDouble()
        julian_day += 0.5
        z = Math.floor(julian_day)
        f = julian_day - z
        a = if (z >= 2299161.0) {
            var a_val = Math.floor((z - 1867216.25) / 36524.25)
            a_val = z + 1 + a_val - Math.floor(a_val / 4)
            a_val
        } else z
        val b_val = a + 1524
        val c_val = Math.floor((b_val - 122.1) / 365.25)
        val d_val = Math.floor(365.25 * c_val)
        val e_val = Math.floor((b_val - d_val) / 30.6001)
        val aux_val = b_val - d_val - Math.floor(30.6001 * e_val) + f
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar[Calendar.DAY_OF_MONTH] = aux_val.toInt()
        var aux_val2 = ((aux_val - calendar[Calendar.DAY_OF_MONTH]) * 24)
        calendar[Calendar.HOUR_OF_DAY] = aux_val2.toInt()
        calendar[Calendar.MINUTE] = ((aux_val2 - calendar[Calendar.HOUR_OF_DAY]) * 60).toInt()
        val m_val: Double = if (e_val < 13.5) e_val - 1 else e_val - 13
        calendar[Calendar.MONTH] = m_val.toInt() - 1
        if (m_val > 2.5) calendar[Calendar.YEAR] = (c_val - 4716).toInt()
        else calendar[Calendar.YEAR] = (c_val - 4715).toInt()
        return toMidnightTime(calendar)
    }

    fun julianDayToTime(julianDay: Long, time: Long) = julianDayToTime(julianDay) + time

    private val JGREG = 15 + 31 * (10 + 12 * 1582)

    fun dayToJulian(time: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        val year = calendar[Calendar.YEAR]
        val month = calendar[Calendar.MONTH] + 1
        val day = calendar[Calendar.DAY_OF_MONTH]
        var julianYear = year
        if (year < 0) julianYear++
        var julianMonth = month
        if (month > 2) julianMonth++
        else {
            julianYear--
            julianMonth += 13
        }
        var julian = Math.floor(365.25 * julianYear) + Math.floor(30.6001 * julianMonth) + day + 1720995.0
        if (day + 31 * (month + 12 * year) >= JGREG) {
            val ja = (0.01 * julianYear).toInt()
            julian += 2 - ja + 0.25 * ja
        }
        return Math.floor(julian).toLong()
    }

    fun getTimeFromTics(tics: Long) = System.currentTimeMillis() + (tics - SystemClock.elapsedRealtime())

    fun getTicsFromTime(time: Long) = SystemClock.elapsedRealtime() + (time - System.currentTimeMillis())

    fun toPrintableTime(context: Context, time: String?): String {
        val timeParts = getTimeParts(time)!!
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, timeParts[0])
        calendar.set(Calendar.MINUTE, timeParts[1])
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return DateFormat.getTimeFormat(context).format(calendar.time)
    }

    fun getTimeFromString(string: String?, future: Boolean): Long {
        val parts = getTimeParts(string)!!
        val now = System.currentTimeMillis()
        val calendar = GregorianCalendar()
        calendar.timeInMillis = now
        calendar.set(GregorianCalendar.HOUR_OF_DAY, parts[0])
        calendar.set(GregorianCalendar.MINUTE, parts[1])
        calendar.set(GregorianCalendar.SECOND, 0)
        calendar.set(GregorianCalendar.MILLISECOND, 0)
        val time = calendar.timeInMillis
        return if (time < now && future) time + DateUtils.DAY_IN_MILLIS else time
    }

    fun getTimeParts(string: String?): IntArray? {
        if (string == null) return null
        val stringParts = string.split(":")
        if (stringParts.size != 2) return null
        return intArrayOf(stringParts[0].toInt(), stringParts[1].toInt())
    }

    private fun compareTimeParts(leftParts: IntArray, rightParts: IntArray): Int {
        if (leftParts[0] < rightParts[0]) return -1
        if (leftParts[0] > rightParts[0]) return 1
        if (leftParts[1] < rightParts[1]) return -1
        if (leftParts[1] > rightParts[1]) return 1
        return 0
    }

    fun inRange(begin: String, end: String, hour: String): Boolean {
        val beginParts = getTimeParts(begin)
        val endParts = getTimeParts(end)
        val hourParts = getTimeParts(hour)
        if (beginParts == null || endParts == null || hourParts == null) return false
        val cmp = compareTimeParts(beginParts, endParts)
        return when {
            cmp == -1 -> compareTimeParts(beginParts, hourParts) <= 0 && compareTimeParts(hourParts, endParts) <= 0
            cmp == 1 -> compareTimeParts(beginParts, hourParts) <= 0 || compareTimeParts(hourParts, endParts) <= 0
            else -> compareTimeParts(beginParts, hourParts) == 0
        }
    }

    fun inRange(rangeLimits: Array<String>, hour: String): Boolean {
        return inRange(rangeLimits[0], rangeLimits[1], hour)
    }
}
