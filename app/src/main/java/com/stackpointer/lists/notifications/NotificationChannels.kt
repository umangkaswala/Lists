package com.stackpointer.lists.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import androidx.core.content.getSystemService

/**
 * The app's notification channels.
 *
 * Channels are created once and then become the *user's* to configure — once a
 * channel exists, changing its importance or sound in code has no effect, so
 * the settings here are effectively permanent. Getting them right the first
 * time matters more than usual.
 */
object NotificationChannels {

    /** Time-based reminder alerts. High importance so they heads-up. */
    const val REMINDERS = "reminders"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(REMINDERS) != null) return

        val channel = NotificationChannel(
            REMINDERS,
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for reminders you've set a time on."
            enableVibration(true)
            setShowBadge(true)
            // USAGE_NOTIFICATION_EVENT, not USAGE_ALARM: an alarm-usage sound
            // ignores the user's notification volume and plays at alarm volume,
            // which is far too aggressive for "bins out".
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
    }
}
