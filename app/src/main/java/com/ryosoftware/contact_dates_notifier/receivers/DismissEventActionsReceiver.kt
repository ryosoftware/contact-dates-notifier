package com.ryosoftware.contact_dates_notifier.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ryosoftware.contact_dates_notifier.BuildConfig
import com.ryosoftware.contact_dates_notifier.MainService
import com.ryosoftware.contact_dates_notifier.data.ApplicationPreferences
import com.ryosoftware.utilities.DateTimeUtilities
import com.ryosoftware.utilities.LogUtilities

class DismissEventActionsReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS_EVENT_UNTIL_NEXT_YEAR = BuildConfig.APPLICATION_ID + ".DismissEventActionsReceiver.DISMISS_EVENT_UNTIL_NEXT_YEAR"
        const val ACTION_DISMISS_EVENT_UNTIL_EVENT_DAY = BuildConfig.APPLICATION_ID + ".DismissEventActionsReceiver.DISMISS_EVENT_UNTIL_EVENT_DAY"
        const val ACTION_DISMISS_TODAY_EVENT = BuildConfig.APPLICATION_ID + ".DismissEventActionsReceiver.DISMISS_TODAY_EVENT"

        const val EXTRA_CONTACT_KEY = "contact-key"
        const val EXTRA_EVENT_TIME = "event-time"
        const val EXTRA_EVENT_DESCRIPTION = "event-description"
        const val EXTRA_NOTIFICATION_ID = "notification-id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        LogUtilities.show(this, "Received event $action")
        when (action) {
            ACTION_DISMISS_EVENT_UNTIL_NEXT_YEAR -> {
                val contactKey = intent.getStringExtra(EXTRA_CONTACT_KEY)
                val eventDescription = intent.getStringExtra(EXTRA_EVENT_DESCRIPTION)
                val eventTime = intent.getLongExtra(EXTRA_EVENT_TIME, 0)
                if (contactKey != null && eventTime != 0L) {
                    LogUtilities.show(
                        this,
                        "Trying to add event at time ${DateTimeUtilities.getDateString(context, eventTime)} to bypassed until next year events for contact identified by $contactKey"
                    )
                    ApplicationPreferences.Contacts.setEventBypassedUntilNextYear(context, contactKey, eventTime, eventDescription)
                    if (intent.hasExtra(EXTRA_NOTIFICATION_ID)) {
                        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                        if (notificationId != -1) MainService.hideNotification(context, notificationId)
                    }
                }
            }
            ACTION_DISMISS_EVENT_UNTIL_EVENT_DAY -> {
                val contactKey = intent.getStringExtra(EXTRA_CONTACT_KEY)
                val eventDescription = intent.getStringExtra(EXTRA_EVENT_DESCRIPTION)
                val eventTime = intent.getLongExtra(EXTRA_EVENT_TIME, 0)
                if (contactKey != null && eventTime != 0L) {
                    LogUtilities.show(
                        this,
                        "Trying to add event at time ${DateTimeUtilities.getDateString(context, eventTime)} to bypassed until event day events for contact identified by $contactKey"
                    )
                    ApplicationPreferences.Contacts.setEventBypassedUntilEventDay(context, contactKey, eventTime, eventDescription)
                    if (intent.hasExtra(EXTRA_NOTIFICATION_ID)) {
                        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                        if (notificationId != -1) MainService.hideNotification(context, notificationId)
                    }
                }
            }
            ACTION_DISMISS_TODAY_EVENT -> {
                if (intent.hasExtra(EXTRA_NOTIFICATION_ID)) {
                    val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                    if (notificationId != -1) MainService.hideNotification(context, notificationId)
                }
            }
        }
    }
}
