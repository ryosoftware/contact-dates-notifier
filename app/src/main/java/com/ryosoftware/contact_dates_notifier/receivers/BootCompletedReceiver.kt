package com.ryosoftware.contact_dates_notifier.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ryosoftware.contact_dates_notifier.MainService
import com.ryosoftware.utilities.LogUtilities

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        LogUtilities.show(this, "Received event $action")
        if (Intent.ACTION_BOOT_COMPLETED == action) {
            MainService.onBootCompleted(context)
        }
    }
}
