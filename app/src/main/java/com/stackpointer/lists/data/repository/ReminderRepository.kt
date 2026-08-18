package com.stackpointer.lists.data.repository

import com.stackpointer.lists.data.dao.ReminderDao
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.recurrence.RRule
import com.stackpointer.lists.recurrence.RRuleExpander
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

/**
 * Everything needed to put one reminder back exactly as it was.
 *
 * Swipe-to-complete and swipe-to-snooze both offer Undo, and both can change
 * more than one field — completing a *repeating* reminder moves its due date
 * instead of setting isCompleted — so undo can't be "flip the one flag back".
 */
data class ReminderUndoSnapshot(
    val id: Long,
    val dueAt: Long?,
    val isAllDay: Boolean,
    val isCompleted: Boolean,
    val completedAt: Long?
)

class ReminderRepository(
    private val reminderDao: ReminderDao,
    // Defaults to the no-op so tests and previews don't need the Android
    // alarm machinery. Production wiring is in AppContainer.
    private val alarms: ReminderAlarms = ReminderAlarms.None
) {
    fun observeActive(): Flow<List<ReminderEntity>> = reminderDao.getActive()

    fun observeById(id: Long): Flow<ReminderEntity?> = reminderDao.observeById(id)

    fun search(query: String): Flow<List<ReminderEntity>> = reminderDao.search(escapeForLike(query))

    companion object {
        /**
         * Neutralises SQL LIKE wildcards in user-typed text. Without this,
         * searching for "100%" or "a_b" matches everything, because `%` and
         * `_` are wildcards. Pairs with `ESCAPE '\'` in the DAO query; the
         * backslash itself has to be escaped first or it would swallow the
         * character after it.
         */
        fun escapeForLike(query: String): String = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    suspend fun snapshotFor(id: Long): ReminderUndoSnapshot? {
        val current = reminderDao.getById(id) ?: return null
        return ReminderUndoSnapshot(
            id = current.id,
            dueAt = current.dueAt,
            // Snooze clears isAllDay, so undo has to restore it too or an
            // all-day reminder is permanently converted to a timed one.
            isAllDay = current.isAllDay,
            isCompleted = current.isCompleted,
            completedAt = current.completedAt
        )
    }

    suspend fun restore(snapshot: ReminderUndoSnapshot) {
        val current = reminderDao.getById(snapshot.id) ?: return
        reminderDao.update(
            current.copy(
                dueAt = snapshot.dueAt,
                isAllDay = snapshot.isAllDay,
                isCompleted = snapshot.isCompleted,
                completedAt = snapshot.completedAt
            )
        )
        alarms.requestSync()
    }

    /** Pushes a reminder's due time out by [minutes] from now. */
    suspend fun snooze(id: Long, minutes: Long) {
        val current = reminderDao.getById(id) ?: return
        val snoozedTo = Instant.now().plusSeconds(minutes * 60).toEpochMilli()
        // Clearing isCompleted matters: snoozing an already-completed reminder
        // would otherwise write a future dueAt that nothing ever schedules, and
        // the snooze would silently vanish.
        reminderDao.update(
            current.copy(
                dueAt = snoozedTo,
                isAllDay = false,
                isCompleted = false,
                completedAt = null
            )
        )
        // Snooze is only a dueAt rewrite, so the re-sync below *is* the entire
        // "reschedule the alert" mechanism. There's no separate snooze alarm.
        alarms.requestSync()
    }

    suspend fun createReminder(
        listId: Long,
        title: String,
        note: String? = null,
        dueAt: Long? = null,
        isAllDay: Boolean = false,
        repeatRule: String? = null
    ): Long {
        val id = reminderDao.insert(
            ReminderEntity(
                listId = listId,
                title = title,
                note = note,
                dueAt = dueAt,
                isAllDay = isAllDay,
                repeatRule = repeatRule.takeIf { dueAt != null },
                seriesStartAt = dueAt.takeIf { repeatRule != null },
                createdAt = Instant.now().toEpochMilli()
            )
        )
        alarms.requestSync()
        return id
    }

    suspend fun updateReminderFields(
        id: Long,
        title: String,
        note: String?,
        listId: Long,
        dueAt: Long?,
        isAllDay: Boolean,
        repeatRule: String?
    ) {
        val current = reminderDao.getById(id) ?: return
        // A rule with no due date has nothing to recur from, so it's dropped.
        val effectiveRule = repeatRule.takeIf { dueAt != null }
        reminderDao.update(
            current.copy(
                title = title,
                note = note,
                listId = listId,
                dueAt = dueAt,
                isAllDay = isAllDay,
                repeatRule = effectiveRule,
                // Re-anchor the series whenever the rule or the due date is
                // edited; keeping a stale anchor would make COUNT/UNTIL count
                // occurrences the user never actually saw.
                seriesStartAt = if (effectiveRule == null) {
                    null
                } else if (current.repeatRule != effectiveRule || current.dueAt != dueAt) {
                    dueAt
                } else {
                    current.seriesStartAt ?: dueAt
                }
            )
        )
        alarms.requestSync()
    }

    /**
     * Completing a repeating reminder rolls it forward to its next occurrence
     * instead of striking it off — that's what makes a repeat a repeat. Only
     * when the series has run out (past UNTIL, or COUNT exhausted) does it
     * complete for real.
     *
     * Returns true if the reminder was rolled forward rather than completed,
     * so callers can word their undo/confirmation accordingly.
     */
    suspend fun setCompleted(id: Long, completed: Boolean): Boolean {
        if (completed) {
            val current = reminderDao.getById(id)
            val rule = RRule.parse(current?.repeatRule)
            val dueAt = current?.dueAt
            if (current != null && rule != null && dueAt != null) {
                val zone = ZoneId.systemDefault()
                val seriesStart = Instant.ofEpochMilli(current.seriesStartAt ?: dueAt).atZone(zone)
                val due = Instant.ofEpochMilli(dueAt).atZone(zone)
                val now = Instant.now().atZone(zone)
                // Skip past every occurrence already missed, not just one: a
                // weekly reminder untouched for a month would otherwise need
                // four completions before it stopped showing up as overdue.
                val next = RRuleExpander.nextAfter(
                    rule = rule,
                    start = seriesStart,
                    after = if (due.isAfter(now)) due else now
                )
                if (next != null) {
                    reminderDao.update(current.copy(dueAt = next.toInstant().toEpochMilli()))
                    alarms.requestSync()
                    return true
                }
            }
        }
        reminderDao.setCompleted(
            id = id,
            completed = completed,
            completedAt = if (completed) Instant.now().toEpochMilli() else null
        )
        alarms.requestSync()
        return false
    }

    suspend fun setImportant(id: Long, important: Boolean) {
        // No requestSync: importance doesn't affect when, or whether, an alarm
        // should fire.
        reminderDao.setImportant(id, important)
    }

    suspend fun softDelete(id: Long) {
        reminderDao.setDeletedAt(id, Instant.now().toEpochMilli())
        alarms.requestSync()
    }
}
