package com.ryosoftware.contact_dates_notifier

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.ryosoftware.contact_dates_notifier.data.ApplicationPreferences
import com.ryosoftware.utilities.LogUtilities

class Main : Application(), Thread.UncaughtExceptionHandler {

    companion object {

        @SuppressLint("StaticFieldLeak")
        lateinit var instance: Context
            private set

        const val BACKGROUND_SERVICE_NOTIFICATION_CHANNEL = "background-service"
        const val NOTIFICATION_EVENT_NOTIFICATION_CHANNEL = "notification-event"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        LogUtilities.tag = BuildConfig.TAG
        LogUtilities.logMode = LogUtilities.DEBUG_ERRORS
        LogUtilities.show(this, "Application started (version is ${BuildConfig.versionName} (${BuildConfig.versionCode}), Android version is ${Build.VERSION.SDK_INT})")
        ApplicationPreferences.initialize(this)
        MainService.schedule(this)
        createNotificationsChannels()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        LogUtilities.show(this, "Uncaught exception", throwable)
    }

    private fun createNotificationsChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(BACKGROUND_SERVICE_NOTIFICATION_CHANNEL, getString(R.string.notification_channel_background_service), NotificationManager.IMPORTANCE_MIN).apply {
                    setSound(null, null)
                    vibrationPattern = null
                }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(NOTIFICATION_EVENT_NOTIFICATION_CHANNEL, getString(R.string.notification_channel_notification_event), NotificationManager.IMPORTANCE_MIN).apply {
                    setSound(null, null)
                }
            )
        }
    }
}
