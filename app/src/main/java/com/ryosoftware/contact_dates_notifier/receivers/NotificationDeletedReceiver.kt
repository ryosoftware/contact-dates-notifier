package com.ryosoftware.contact_dates_notifier.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ryosoftware.contact_dates_notifier.BuildConfig
import com.ryosoftware.contact_dates_notifier.MainService
import com.ryosoftware.utilities.LogUtilities

class NotificationDeletedReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NOTIFICATION_DELETED = BuildConfig.APPLICATION_ID + ".NotificationDeletedReceiver.NOTIFICATION_DELETED"
        const val EXTRA_NOTIFICATION_ID = "notification-id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        LogUtilities.show(this, "Received event $action")
        if (ACTION_NOTIFICATION_DELETED == action) {
            if (intent.hasExtra(EXTRA_NOTIFICATION_ID)) {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notificationId != -1) MainService.hideNotification(context, notificationId)
            }
        }
    }
}
