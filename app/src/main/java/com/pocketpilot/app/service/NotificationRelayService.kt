package com.pocketpilot.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Reads notifications for triage (sender, app, text). Content is never sent wholesale to the model. */
class NotificationRelayService : NotificationListenerService() {
    companion object {
        @Volatile
        var latest: List<String> = emptyList()
            private set
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification.extras
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val line = "[${sbn.packageName}] $title: $text".take(200)
        latest = (listOf(line) + latest).take(20)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Boot / scheduled wake: restart the pilot service so the proactive
        // loop and alarms keep working. No work is done silently.
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, PilotCoreService::class.java))
        }
    }
}
