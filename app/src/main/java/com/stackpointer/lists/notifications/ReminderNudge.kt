package com.stackpointer.lists.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.getSystemService
import com.stackpointer.lists.ListsApplication
import kotlinx.coroutines.launch

/**
 * Settings S16's "Nudge me again if ignored — repeats every 10 minutes, twice".
 *
 * Deliberately a self-contained mechanism rather than something bolted into
 * [AlarmScheduler]. That scheduler's whole design is "cancel everything and
 * re-derive it from the reminders table", and a nudge isn't derivable from the
 * table at all — it depends on transient facts (a notification was posted, it
 * is still sitting unanswered in the shade) that no column records. Folding it
 * in would have meant either persisting notification state or making the
 * planner impure. Its own alarm namespace keeps the tested Phase 5 path
 * untouched.
 *
 * Nothing cancels a pending nudge when the user deals with the reminder, and
 * that is on purpose: [ReminderNudgeReceiver] re-checks the world when it
 * fires, which catches every way a reminder can be answered — Done, Snooze,
 * swiping the notification away, completing it in the app, deleting it — with
 * one rule instead of six call sites that each have to remember. The cost is
 * at most one wasted wake-up per reminder, the same trade [AlarmScheduler]
 * already makes for stale alarms.
 */
object ReminderNudge {

    /** Design S16: "Repeats every 10 minutes, twice." */
    const val INTERVAL_MILLIS = 10L * 60L * 1000L
    const val MAX_ATTEMPTS = 2

    fun schedule(context: Context, reminderId: Long, attempt: Int) {
        if (attempt > MAX_ATTEMPTS) return
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pendingIntent = pendingIntent(context, reminderId, attempt, PendingIntent.FLAG_UPDATE_CURRENT)
            ?: return
        // Inexact on purpose. A nudge is "in about ten minutes", and spending
        // the app's exact-alarm budget — or asking for the permission again —
        // to be punctual about a reminder the user is already ignoring would be
        // the wrong trade.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVAL_MILLIS,
            pendingIntent
        )
    }

    /**
     * True while this reminder's notification is still sitting in the shade.
     *
     * This is the whole definition of "ignored". Tapping it, pressing Done or
     * Snooze, or swiping it away all remove it, so all three read as answered.
     */
    fun notificationStillShowing(context: Context, reminderId: Long): Boolean {
        val manager = context.getSystemService<NotificationManager>() ?: return false
        return runCatching {
            manager.activeNotifications.any { it.id == reminderId.toInt() }
        }.getOrDefault(false)
    }

    /**
     * The attempt number is part of the `data` URI, not just the extras.
     * PendingIntent identity ignores extras entirely (see [AlarmScheduler]),
     * so without it the second nudge would be the same PendingIntent as the
     * first and would simply replace it.
     */
    private fun pendingIntent(
        context: Context,
        reminderId: Long,
        attempt: Int,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, ReminderNudgeReceiver::class.java).apply {
            action = ACTION_NUDGE
            data = Uri.parse("lists://nudge/$reminderId/$attempt")
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_ATTEMPT, attempt)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(reminderId, attempt),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(reminderId: Long, attempt: Int): Int =
        (reminderId.toInt() * 100) + 50 + attempt

    const val ACTION_NUDGE = "com.stackpointer.lists.REMINDER_NUDGE"
    const val EXTRA_REMINDER_ID = "reminderId"
    const val EXTRA_ATTEMPT = "attempt"
}

/** Re-posts an unanswered reminder alert, then queues the next nudge. */
class ReminderNudgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderNudge.EXTRA_REMINDER_ID, -1L)
        val attempt = intent.getIntExtra(ReminderNudge.EXTRA_ATTEMPT, 1)
        if (reminderId <= 0L) return

        val app = context.applicationContext as? ListsApplication ?: return
        val container = app.container

        // Room refuses main-thread queries and onReceive runs on it, so this
        // has to go async — and pendingResult.finish() must run on every path
        // or the device is held awake.
        val pendingResult = goAsync()
        container.applicationScope.launch {
            try {
                // Answered in the meantime: nothing to nudge about, and no
                // further attempts either.
                if (!ReminderNudge.notificationStillShowing(context, reminderId)) return@launch

                container.reminderAlerts.renotify(reminderId)
                ReminderNudge.schedule(context, reminderId, attempt + 1)
            } catch (e: Exception) {
                Log.e(TAG, "Nudge $attempt failed for $reminderId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ReminderNudgeReceiver"
    }
}
