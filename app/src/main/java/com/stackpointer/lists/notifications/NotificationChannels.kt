package com.stackpointer.lists.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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

    /**
     * How the Reminders channel currently alerts — "Sound + vibrate",
     * "Vibrate only", "Silent", "Off" — for design S16's Alert style row.
     *
     * Read from the OS every time rather than stored, because after creation
     * the channel belongs to the user: they can mute or unmute it from system
     * Settings and the app is never told.
     */
    fun alertStyleSummary(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "Sound + vibrate"
        val manager = context.getSystemService<NotificationManager>()
        val channel = manager?.getNotificationChannel(REMINDERS)
            // Created lazily on the first alert, so "not there yet" means the
            // defaults above rather than "no alerts".
            ?: return "Sound + vibrate"

        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return "Off"
        // Below DEFAULT the channel keeps whatever sound URI it was created
        // with, but Android never plays it. Reading the URI alone would report
        // "Sound + vibrate" for a channel the user has explicitly silenced.
        if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT) return "Silent"
        val hasSound = channel.sound != null
        val vibrates = channel.shouldVibrate()
        return when {
            hasSound && vibrates -> "Sound + vibrate"
            hasSound -> "Sound only"
            vibrates -> "Vibrate only"
            else -> "Silent"
        }
    }

    /**
     * Opens the *system's* settings for this channel.
     *
     * S16 draws Alert style as an in-app sub-screen, and it deliberately isn't
     * one. A notification channel is immutable once created: importance, sound
     * and vibration can never be changed by the app again, only by the user.
     * An in-app control would either do nothing or have to delete and recreate
     * the channel under a new id, which throws away every tweak the user has
     * made. Handing them the real switches is the only honest version of this
     * row.
     */
    fun channelSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, REMINDERS)
        } else {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
}
