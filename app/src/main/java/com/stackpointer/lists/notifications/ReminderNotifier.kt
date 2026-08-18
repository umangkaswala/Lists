package com.stackpointer.lists.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.stackpointer.lists.MainActivity
import com.stackpointer.lists.R
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.recurrence.RRule
import com.stackpointer.lists.recurrence.rruleShortLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds and posts the reminder alert (design screen S17).
 *
 * One notification per reminder, keyed by the reminder's row id, so a
 * re-fired or re-scheduled reminder replaces its own notification instead of
 * stacking up duplicates.
 */
object ReminderNotifier {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun show(
        context: Context,
        reminder: ReminderEntity,
        listName: String?,
        /**
         * Replaces the usual time/repeat line for a place-triggered alert, so
         * the notification can say what actually happened ("You just left
         * Work") rather than a due time it doesn't have.
         */
        overrideSubtitle: String? = null
    ) {
        NotificationChannels.ensure(context)

        // Posting without POST_NOTIFICATIONS throws on some OEM builds rather
        // than silently no-op'ing, and this runs from a BroadcastReceiver where
        // an exception takes the whole process down.
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        val text = overrideSubtitle ?: subtitle(reminder, listName)
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openReminderIntent(context, reminder.id))
            .addAction(
                0,
                "Done",
                ReminderActionReceiver.donePendingIntent(context, reminder.id)
            )
            .addAction(
                0,
                "10 min",
                ReminderActionReceiver.snoozePendingIntent(context, reminder.id, 10)
            )
            .addAction(
                0,
                "1 hour",
                ReminderActionReceiver.snoozePendingIntent(context, reminder.id, 60)
            )
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(reminder.id), notification)
    }

    fun cancel(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    /**
     * Reminder ids are Longs but notification ids are Ints. Reminders are
     * autoGenerate primary keys on a personal database, so the low 32 bits are
     * unique in any realistic lifetime.
     */
    private fun notificationId(reminderId: Long): Int = reminderId.toInt()

    /** "7:00 PM · repeats every Tuesday · Home", omitting whichever parts don't apply. */
    private fun subtitle(reminder: ReminderEntity, listName: String?): String {
        val parts = mutableListOf<String>()

        if (!reminder.isAllDay) {
            reminder.dueAt?.let {
                parts += Instant.ofEpochMilli(it)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .format(timeFormatter)
            }
        } else {
            parts += "All day"
        }

        RRule.parse(reminder.repeatRule)?.let { rule ->
            val anchor = Instant.ofEpochMilli(reminder.seriesStartAt ?: reminder.dueAt ?: 0L)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            parts += "repeats " + rruleShortLabel(rule, anchor)
                .replaceFirstChar { it.lowercase(Locale.ENGLISH) }
        }

        listName?.let { parts += it }

        return parts.joinToString(" · ")
    }

    private fun openReminderIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getActivity(
            context,
            notificationId(reminderId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
