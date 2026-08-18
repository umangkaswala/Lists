package com.stackpointer.lists.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.stackpointer.lists.ListsApplication
import kotlinx.coroutines.launch

/**
 * Handles the Done / Snooze buttons on a reminder notification.
 *
 * Both actions have to touch Room, which is a suspend call, while a
 * BroadcastReceiver's onReceive must return within roughly ten seconds and its
 * process may be killed the moment it does. [goAsync] holds the receiver alive
 * across the coroutine and releases it when the work finishes.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0L) return

        val app = context.applicationContext as? ListsApplication ?: return
        val container = app.container

        // Dismiss immediately, on the main thread, so the notification doesn't
        // linger while the database work happens.
        ReminderNotifier.cancel(context, reminderId)

        val pendingResult = goAsync()
        // The application scope, not a scope of this receiver's own: the
        // receiver is gone the moment onReceive returns, and a scope tied to it
        // would cancel the database write half-finished.
        container.applicationScope.launch {
            try {
                // Both paths move dueAt — Done rolls a repeating reminder to its
                // next occurrence, Snooze pushes it out — and both repository
                // calls re-sync the alarms themselves, so there is no separate
                // "reschedule the snooze" machinery to get wrong.
                when (intent.action) {
                    ACTION_DONE -> container.reminderRepository.setCompleted(reminderId, true)
                    ACTION_SNOOZE -> {
                        val minutes = intent.getLongExtra(EXTRA_SNOOZE_MINUTES, 10L)
                        container.reminderRepository.snooze(reminderId, minutes)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle ${intent.action} for $reminderId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderActionReceiver"
        const val ACTION_DONE = "com.stackpointer.lists.action.DONE"
        const val ACTION_SNOOZE = "com.stackpointer.lists.action.SNOOZE"
        const val EXTRA_REMINDER_ID = "reminderId"
        const val EXTRA_SNOOZE_MINUTES = "snoozeMinutes"

        fun donePendingIntent(context: Context, reminderId: Long): PendingIntent =
            pendingIntent(context, reminderId, ACTION_DONE, requestCodeOffset = 1)

        fun snoozePendingIntent(context: Context, reminderId: Long, minutes: Long): PendingIntent =
            pendingIntent(
                context,
                reminderId,
                ACTION_SNOOZE,
                // Each snooze length needs its own request code, or the second
                // PendingIntent would be treated as "the same" as the first and
                // both buttons would snooze by the same amount.
                requestCodeOffset = 2 + minutes.toInt(),
                minutes = minutes
            )

        private fun pendingIntent(
            context: Context,
            reminderId: Long,
            action: String,
            requestCodeOffset: Int,
            minutes: Long? = null
        ): PendingIntent {
            val intent = Intent(context, ReminderActionReceiver::class.java).apply {
                this.action = action
                // The data URI, not the extras, is what makes two reminders'
                // buttons distinct — see the note on requestCode below.
                data = Uri.parse("lists://reminder/$reminderId/$requestCodeOffset")
                putExtra(EXTRA_REMINDER_ID, reminderId)
                minutes?.let { putExtra(EXTRA_SNOOZE_MINUTES, it) }
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode(reminderId, requestCodeOffset),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * PendingIntents are matched on (requestCode, action, data, type,
         * class, categories) — *not* on extras. Two reminders' Done buttons
         * would therefore collide unless the request code differs, and the
         * second one created would silently reuse the first one's extras.
         */
        private fun requestCode(reminderId: Long, offset: Int): Int =
            (reminderId.toInt() * 100) + offset
    }
}
