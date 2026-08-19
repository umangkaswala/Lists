package com.stackpointer.lists.notifications

import android.content.Context
import com.stackpointer.lists.data.dao.ReminderDao
import com.stackpointer.lists.data.dao.ReminderListDao
import com.stackpointer.lists.data.prefs.SettingsStore
import com.stackpointer.lists.data.prefs.allDayAlertTime
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * Turns "alarm N fired" into "post reminder N's notification, if it still
 * deserves one".
 *
 * The re-validation is the point. An alarm is a message from a few minutes or
 * a few months ago; between scheduling and firing the reminder may have been
 * completed, deleted, moved, or had its whole list removed. Posting blindly
 * would resurrect reminders the user has already dealt with.
 */
class ReminderAlerts(
    context: Context,
    private val reminderDao: ReminderDao,
    private val listDao: ReminderListDao,
    private val settingsStore: SettingsStore
) {

    private val appContext = context.applicationContext

    suspend fun notifyDue(reminderId: Long) {
        if (!post(reminderId)) return
        // Settings S16: "Nudge me again if ignored." Only the time-based path
        // nudges — a place alert says "you just left Work", and repeating that
        // ten minutes and half a mile later would be telling the user something
        // that is no longer true.
        if (settingsStore.settings.first().nudgeWhenIgnored) {
            ReminderNudge.schedule(appContext, reminderId, attempt = 1)
        }
    }

    /**
     * Re-posts an alert the user hasn't answered. Runs the same validation as
     * [notifyDue] — a reminder completed from inside the app between the two
     * must not come back — but leaves queuing the next attempt to
     * [ReminderNudgeReceiver], which is the thing that knows the attempt count.
     */
    suspend fun renotify(reminderId: Long) {
        post(reminderId)
    }

    /** Returns whether a notification was actually posted. */
    private suspend fun post(reminderId: Long): Boolean {
        val reminder = reminderDao.getById(reminderId) ?: return false
        if (reminder.isCompleted || reminder.deletedAt != null || reminder.dueAt == null) return false

        // Re-derive the trigger instead of trusting the alarm: if the reminder
        // was edited to a later time, this alarm is stale and must not fire.
        // A trigger already in the past is fine — Doze can hold an alarm back
        // by several minutes, and being a bit late is not being wrong.
        val alertTime = settingsStore.settings.first().allDayAlertTime
        val triggerAt = AlarmPlanner.triggerAtFor(reminder, ZoneId.systemDefault(), alertTime)
            ?: return false
        if (triggerAt > System.currentTimeMillis() + STALE_TOLERANCE_MILLIS) return false

        val listName = listDao.getById(reminder.listId)?.name
        // The notifier's own answer, not an assumption: without
        // POST_NOTIFICATIONS it posts nothing, and queueing a nudge to re-post
        // an alert that was never shown would wake the device for nothing.
        return ReminderNotifier.show(appContext, reminder, listName)
    }

    private companion object {
        /**
         * How far ahead of its trigger an alarm may legitimately arrive.
         * AlarmManager can fire fractionally early, and a reminder scheduled
         * for exactly now would otherwise be judged "not due yet".
         */
        const val STALE_TOLERANCE_MILLIS = 5L * 60L * 1000L
    }
}
