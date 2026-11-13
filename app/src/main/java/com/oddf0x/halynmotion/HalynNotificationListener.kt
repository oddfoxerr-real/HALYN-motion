package com.oddf0x.halynmotion

import android.app.Notification
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional: Android does not expose other apps' notification events to a
 * wallpaper without explicit notification-listener consent.
 *
 * We never read, store, log, or transmit message text, sender, package name,
 * or notification IDs; only the current monotonic event time is saved.
 */
class HalynNotificationListener : NotificationListenerService() {
    private var lastSignalMs = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSignalMs < 500L) return
        lastSignalMs = now
        getSharedPreferences("halyn_motion_settings", MODE_PRIVATE)
            .edit()
            .putLong("last_notification_signal_ms", now)
            .apply()
    }
}
