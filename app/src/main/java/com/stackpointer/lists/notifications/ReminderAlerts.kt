package com.stackpointer.lists.notifications

import android.content.Context
import com.stackpointer.lists.data.dao.ReminderDao
import com.stackpointer.lists.data.dao.ReminderListDao
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
    private val listDao: ReminderListDao
) {

    private val appContext = context.applicationContext

    suspend fun notifyDue(reminderId: Long) {
        val reminder = reminderDao.getById(reminderId) ?: return
        if (reminder.isCompleted || reminder.deletedAt != null || reminder.dueAt == null) return

        // Re-derive the trigger instead of trusting the alarm: if the reminder
        // was edited to a later time, this alarm is stale and must not fire.
        // A trigger already in the past is fine — Doze can hold an alarm back
        // by several minutes, and being a bit late is not being wrong.
        val triggerAt = AlarmPlanner.triggerAtFor(reminder, ZoneId.systemDefault()) ?: return
        if (triggerAt > System.currentTimeMillis() + STALE_TOLERANCE_MILLIS) return

        val listName = listDao.getById(reminder.listId)?.name
        ReminderNotifier.show(appContext, reminder, listName)
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
