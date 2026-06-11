package com.ryosoftware.contact_dates_notifier

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.ryosoftware.contact_dates_notifier.data.ApplicationContactsDriver
import com.ryosoftware.utilities.DateTimeUtilities
import com.ryosoftware.contact_dates_notifier.data.ApplicationPreferences
import com.ryosoftware.contact_dates_notifier.data.DeviceContactsDriver
import com.ryosoftware.contact_dates_notifier.data.model.ApplicationContact
import com.ryosoftware.contact_dates_notifier.data.model.Contact
import com.ryosoftware.contact_dates_notifier.data.model.Contact.Event
import com.ryosoftware.contact_dates_notifier.receivers.DismissEventActionsReceiver
import com.ryosoftware.contact_dates_notifier.receivers.NotificationDeletedReceiver
import com.ryosoftware.contact_dates_notifier.ui.activities.AppActivity
import com.ryosoftware.contact_dates_notifier.data.model.DeviceContact
import com.ryosoftware.utilities.LogUtilities
import java.util.*
import androidx.core.graphics.createBitmap
import org.json.JSONObject

class MainService(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val ACTION_RUN_SERVICE = BuildConfig.APPLICATION_ID + ".RUN_SERVICE"

        private const val FOREGROUND_SERVICE_NOTIFICATION_ID = 101

        private const val FIRST_EVENT_NOTIFICATION_ID = FOREGROUND_SERVICE_NOTIFICATION_ID + 1

        private const val NOTIFICATIONS_IDS_KEY = "notifications-ids"
        private const val LAST_SERVICE_EXECUTION_TIME_KEY = "last-notifier-execution"

        fun hideNotification(context: Context, notificationId: Int) {
            NotificationManagerCompat.from(context).cancel(FIRST_EVENT_NOTIFICATION_ID + notificationId)
            val notificationsMap = readNotificationsMap(context).toMutableMap()
            val entry = notificationsMap.entries.find { it.value == notificationId } ?: return
            notificationsMap.remove(entry.key)
            if (notificationsMap.isEmpty()) {
                ApplicationPreferences.removeKey(context, NOTIFICATIONS_IDS_KEY)
            } else {
                saveNotificationsMap(context, notificationsMap)
            }
        }

        private fun getEventKey(contact: Contact, event: Event): String {
            val prefix = if (contact is DeviceContact) "device-event-" else "app-event-"
            val contactId = if (contact is DeviceContact) {
                val dc = contact as DeviceContact
                dc.immutableContactIdentifier ?: dc.databaseContactIdentifier.toString()
            } else {
                (contact as ApplicationContact).id.toString()
            }
            return "$prefix$contactId-${event.time}"
        }

        private fun readNotificationsMap(context: Context): Map<String, Int> {
            val json = ApplicationPreferences.getString(context, NOTIFICATIONS_IDS_KEY, null) ?: return emptyMap()
            return try {
                val obj = JSONObject(json)
                val map = mutableMapOf<String, Int>()
                for (key in obj.keys()) {
                    map[key] = obj.getInt(key)
                }
                map
            } catch (e: Exception) {
                emptyMap()
            }
        }

        private fun saveNotificationsMap(context: Context, map: Map<String, Int>) {
            if (map.isEmpty()) {
                ApplicationPreferences.removeKey(context, NOTIFICATIONS_IDS_KEY)
            } else {
                ApplicationPreferences.putString(context, NOTIFICATIONS_IDS_KEY, JSONObject(map).toString())
            }
        }

        fun schedule(context: Context) {
            if (!ApplicationPreferences.getBoolean(context, ApplicationPreferences.ENABLE_NOTIFICATIONS_KEY, ApplicationPreferences.ENABLE_NOTIFICATIONS_DEFAULT)) return
            val hour = ApplicationPreferences.getString(
                context,
                ApplicationPreferences.NOTIFICATION_HOUR_KEY,
                ApplicationPreferences.NOTIFICATION_HOUR_DEFAULT
            )
            val nextTime = DateTimeUtilities.getTimeFromString(hour, true)
            val initialDelay = nextTime - System.currentTimeMillis()
            val request = OneTimeWorkRequest.Builder(MainService::class.java)
                .setInitialDelay(initialDelay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                MainService::class.java.name,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun startService(context: Context) {
            if (!ApplicationPreferences.getBoolean(context, ApplicationPreferences.ENABLE_NOTIFICATIONS_KEY, ApplicationPreferences.ENABLE_NOTIFICATIONS_DEFAULT)) return
            val request = OneTimeWorkRequest.Builder(MainService::class.java)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                MainService::class.java.name,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun onBootCompleted(context: Context) = startService(context)

        fun onPackageReplaced(context: Context) = startService(context)
    }

    private var serviceNeedsToBeExecuted = false

    override fun doWork(): Result {
        try {
            setForegroundAsync(createForegroundInfo())
        } catch (e: Exception) {
            LogUtilities.show(this, "Error setting foreground: ${e.message}")
        }
        doProcess()
        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val context = applicationContext
        val notification = NotificationCompat.Builder(context, Main.BACKGROUND_SERVICE_NOTIFICATION_CHANNEL)
            .setContentText(context.getString(R.string.running_background_tasks))
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(FOREGROUND_SERVICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(FOREGROUND_SERVICE_NOTIFICATION_ID, notification)
        }
    }

    private data class BatchPrefs(
        val notNotifiedAccounts: Set<String>?,
        val notificationDaysLeft: Int,
        val clearableNotification: Boolean,
        val clearanleNotificationNotForTodayEvents: Boolean
    )

    private fun readBatchPrefs(): BatchPrefs {
        val context = applicationContext
        return BatchPrefs(
            notNotifiedAccounts = ApplicationPreferences.getStrings(
                context, ApplicationPreferences.NOT_NOTIFIED_ACCOUNTS_KEY,
                ApplicationPreferences.NOT_NOTIFIED_ACCOUNTS_DEFAULT
            ),
            notificationDaysLeft = ApplicationPreferences.getInteger(
                context, ApplicationPreferences.NOTIFICATION_DAYS_LEFT_KEY,
                ApplicationPreferences.NOTIFICATION_DAYS_LEFT_DEFAULT
            ),
            clearableNotification = ApplicationPreferences.getBoolean(
                context, ApplicationPreferences.CLEARABLE_NOTIFICATION_KEY,
                ApplicationPreferences.CLEARABLE_NOTIFICATION_DEFAULT
            ),
            clearanleNotificationNotForTodayEvents = ApplicationPreferences.getBoolean(
                context, ApplicationPreferences.CLEARABLE_NOTIFICATION_NOT_FOR_TODAY_EVENTS_KEY,
                ApplicationPreferences.CLEARABLE_NOTIFICATION_NOT_FOR_TODAY_EVENTS_DEFAULT
            )
        )
    }

    private fun doProcess() {
        val context = applicationContext
        if (!ApplicationPreferences.getBoolean(context, ApplicationPreferences.ENABLE_NOTIFICATIONS_KEY, ApplicationPreferences.ENABLE_NOTIFICATIONS_DEFAULT)) return
        val lastServiceExecution = ApplicationPreferences.getLong(context, LAST_SERVICE_EXECUTION_TIME_KEY, 0)
        val programmedServiceExecutionForToday = DateTimeUtilities.getTimeFromString(
            ApplicationPreferences.getString(context, ApplicationPreferences.NOTIFICATION_HOUR_KEY, ApplicationPreferences.NOTIFICATION_HOUR_DEFAULT),
            false
        )
        val now = System.currentTimeMillis()
        serviceNeedsToBeExecuted = programmedServiceExecutionForToday in (lastServiceExecution + 1)..now
        val batchPrefs = readBatchPrefs()
        val activeEvents = getContacts(batchPrefs)
        val hasTodayOngoing = batchPrefs.clearanleNotificationNotForTodayEvents && activeEvents.any { it.event.daysUntilNextIteration() == 0 }
        if (serviceNeedsToBeExecuted || !batchPrefs.clearableNotification || hasTodayOngoing) {
            onProcessEnded(activeEvents)
        }
    }

    private fun getContacts(batchPrefs: BatchPrefs): List<ActiveContactEvent> {
        val activeContactsEvents = mutableListOf<ActiveContactEvent>()
        val context = applicationContext
        val deviceContacts = DeviceContactsDriver.get(context)
        for (contact in deviceContacts) {
            for (event in getActiveAlerts(contact, batchPrefs)) {
                activeContactsEvents.add(ActiveContactEvent(contact, event))
            }
        }
        val appContacts = ApplicationContactsDriver.get(context)
        for (contact in appContacts) {
            for (event in getActiveAlerts(contact, batchPrefs)) {
                activeContactsEvents.add(ActiveContactEvent(contact, event))
            }
        }
        activeContactsEvents.sortWith(ActiveContactEventsComparator())
        return activeContactsEvents
    }

    private fun getActiveAlerts(contact: Contact, batchPrefs: BatchPrefs): List<Event> {
        val context = applicationContext
        val events = mutableListOf<Event>()
        if (!ApplicationPreferences.Contacts.isDontDisplayAlertsFromThisContactEnabled(context, contact)) {
            val notNotifiedAccounts = batchPrefs.notNotifiedAccounts
            val notificationDaysLeft = batchPrefs.notificationDaysLeft
            for (i in 0 until contact.eventsCount) {
                val event = contact.getEvent(i)
                var bypassedReason: String? = null
                val daysLeft = event.daysUntilNextIteration()
                if (daysLeft > notificationDaysLeft) bypassedReason = "outside window"
                if (bypassedReason == null && notNotifiedAccounts != null) {
                    for (account in notNotifiedAccounts) {
                        if ((account == ApplicationPreferences.APP_CONTACTS_ACCOUNT_NAME && contact is ApplicationContact) ||
                            (account == ApplicationPreferences.LOCAL_CONTACTS_ACCOUNT_NAME && contact is DeviceContact && contact.accountName == null) ||
                            (account != ApplicationPreferences.APP_CONTACTS_ACCOUNT_NAME && account != ApplicationPreferences.LOCAL_CONTACTS_ACCOUNT_NAME && account == contact.accountName)
                        ) {
                            bypassedReason = "not noticeable account"
                            break
                        }
                    }
                }
                if (bypassedReason == null && ApplicationPreferences.Contacts.isEventDisabled(
                        context, contact, event
                    )
                ) bypassedReason = "disabled"
                if (bypassedReason == null && ApplicationPreferences.Contacts.isEventBypassedUntilNextYear(
                        context, contact, event.nextIteration, event.description
                    )
                ) bypassedReason = "bypassed until next year"
                if (bypassedReason == null && ApplicationPreferences.Contacts.isEventBypassedUntilEventDay(
                        context, contact, event.nextIteration, event.description
                    ) && daysLeft != 0
                ) bypassedReason = "bypassed until event day"
                if (bypassedReason == null) events.add(event)
            }
        }
        return events
    }

    private fun onProcessEnded(activeContactsEvents: List<ActiveContactEvent>) {
        val context = applicationContext
        ApplicationPreferences.putLong(context, LAST_SERVICE_EXECUTION_TIME_KEY, System.currentTimeMillis())
        schedule(context)
        if (activeContactsEvents.isEmpty()) {
            NotificationManagerCompat.from(context).cancelAll()
            ApplicationPreferences.removeKey(context, NOTIFICATIONS_IDS_KEY)
        } else {
            showNotification(activeContactsEvents)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun showNotification(activeEvents: List<ActiveContactEvent>) {
        val context = applicationContext
        if (AppActivity.isRunning()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val notificationsManager = NotificationManagerCompat.from(context)

        val notificationsMap = readNotificationsMap(context).toMutableMap()
        var nextAvailableId = (notificationsMap.values.maxOrNull() ?: 0) + 1
        val activeKeys = mutableSetOf<String>()
        val defaults = if (serviceNeedsToBeExecuted) (Notification.DEFAULT_LIGHTS or Notification.DEFAULT_SOUND) else 0
        var soundAssigned = false

        val batchPrefs = readBatchPrefs()

        for (i in activeEvents.indices) {
            val ace = activeEvents[i]
            val eventKey = getEventKey(ace.contact, ace.event)
            activeKeys.add(eventKey)
            val isNew = eventKey !in notificationsMap
            val notificationId = notificationsMap[eventKey] ?: run {
                val newId = nextAvailableId
                notificationsMap[eventKey] = newId
                nextAvailableId++
                newId
            }

            val eventBuilder = NotificationCompat.Builder(context, Main.NOTIFICATION_EVENT_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notification_one)
                .setContentTitle(ace.contact.name ?: "")
                .setDefaults(if (!soundAssigned && isNew) { soundAssigned = true; defaults } else 0)
            val days = ace.event.daysUntilNextIteration()
            val isTodayEvent = days == 0
            val isClearable = if (isTodayEvent && batchPrefs.clearanleNotificationNotForTodayEvents) false else batchPrefs.clearableNotification
            eventBuilder
                .setAutoCancel(isClearable)
                .setOngoing(!isClearable)
            eventBuilder.setContentText(
                    if (days == 0) context.getString(
                    if (ace.event.hasYear) R.string.today_contact_event_with_iteration_number_notification_text
                    else R.string.today_contact_event_notification_text,
                    ace.event.description, ace.event.yearsUntilNextIteration
                ) else context.resources.getQuantityString(
                    if (ace.event.hasYear) R.plurals.number_of_days_to_contact_event_with_iteration_number_notification_text
                    else R.plurals.number_of_days_to_contact_event_notification_text,
                    days, days, ace.event.description, ace.event.yearsUntilNextIteration
                )
            )
            val photo: Drawable? = ace.contact.getPhoto(context)
            if (photo is BitmapDrawable) {
                val bmp = photo.bitmap
                val circleBitmap = try {
                    val output = createBitmap(bmp.width, bmp.height)
                    val canvas = Canvas(output)
                    val paint = Paint().apply { isAntiAlias = true }
                    canvas.drawARGB(0, 0, 0, 0)
                    canvas.drawOval(RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()), paint)
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    canvas.drawBitmap(bmp, 0f, 0f, paint)
                    output
                } catch (e: Exception) {
                    LogUtilities.show(this, e)
                    bmp
                }
                eventBuilder.setLargeIcon(circleBitmap)
            }
            if (days > 0) {
                eventBuilder.addAction(
                    0, context.getString(R.string.dismiss_until_next_year),
                    PendingIntent.getBroadcast(
                        context, FIRST_EVENT_NOTIFICATION_ID + 4 * notificationId,
                        Intent(context, DismissEventActionsReceiver::class.java)
                            .setAction(DismissEventActionsReceiver.ACTION_DISMISS_EVENT_UNTIL_NEXT_YEAR)
                            .putExtra(DismissEventActionsReceiver.EXTRA_CONTACT_KEY, ApplicationPreferences.Contacts.getContactKeyForBypassedUntilNextYearEvents(ace.contact))
                            .putExtra(DismissEventActionsReceiver.EXTRA_EVENT_TIME, ace.event.nextIteration)
                            .putExtra(DismissEventActionsReceiver.EXTRA_EVENT_DESCRIPTION, ace.event.description)
                            .putExtra(DismissEventActionsReceiver.EXTRA_NOTIFICATION_ID, notificationId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                eventBuilder.addAction(
                    0, context.getString(R.string.dismiss_until_event_date),
                    PendingIntent.getBroadcast(
                        context, FIRST_EVENT_NOTIFICATION_ID + 4 * notificationId + 1,
                        Intent(context, DismissEventActionsReceiver::class.java)
                            .setAction(DismissEventActionsReceiver.ACTION_DISMISS_EVENT_UNTIL_EVENT_DAY)
                            .putExtra(DismissEventActionsReceiver.EXTRA_CONTACT_KEY, ApplicationPreferences.Contacts.getContactKeyForBypassedUntilEventDayEvents(ace.contact))
                            .putExtra(DismissEventActionsReceiver.EXTRA_EVENT_TIME, ace.event.nextIteration)
                            .putExtra(DismissEventActionsReceiver.EXTRA_EVENT_DESCRIPTION, ace.event.description)
                            .putExtra(DismissEventActionsReceiver.EXTRA_NOTIFICATION_ID, notificationId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else if (isTodayEvent && batchPrefs.clearanleNotificationNotForTodayEvents) {
                eventBuilder.addAction(
                    0, context.getString(R.string.dismiss),
                    PendingIntent.getBroadcast(
                        context, FIRST_EVENT_NOTIFICATION_ID + 4 * notificationId,
                        Intent(context, NotificationDeletedReceiver::class.java)
                            .putExtra(NotificationDeletedReceiver.EXTRA_NOTIFICATION_ID, notificationId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            eventBuilder.setDeleteIntent(
                PendingIntent.getBroadcast(
                    context, FIRST_EVENT_NOTIFICATION_ID + 4 * notificationId + 2,
                    Intent(context, NotificationDeletedReceiver::class.java)
                        .putExtra(NotificationDeletedReceiver.EXTRA_NOTIFICATION_ID, notificationId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            eventBuilder.setContentIntent(
                PendingIntent.getActivity(
                    context, FIRST_EVENT_NOTIFICATION_ID + 4 * notificationId + 3,
                    Intent(context, AppActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            notificationsManager.notify(FIRST_EVENT_NOTIFICATION_ID + notificationId, eventBuilder.build())
        }

        notificationsMap.keys.removeAll { it !in activeKeys }
        saveNotificationsMap(context, notificationsMap)
    }

    private class ActiveContactEvent(val contact: Contact, val event: Event)

    private class ActiveContactEventsComparator : Comparator<ActiveContactEvent> {
        override fun compare(l: ActiveContactEvent, r: ActiveContactEvent): Int {
            return if (l.event.nextIteration > r.event.nextIteration) -1
            else if (l.event.nextIteration < r.event.nextIteration) 1
            else (r.contact.name ?: "").compareTo(l.contact.name ?: "", ignoreCase = true)
        }
    }
}
