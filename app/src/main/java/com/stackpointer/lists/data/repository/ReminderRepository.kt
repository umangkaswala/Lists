package com.stackpointer.lists.data.repository

import com.stackpointer.lists.data.dao.CompletedEntryRow
import com.stackpointer.lists.data.dao.CompletionDao
import com.stackpointer.lists.data.dao.ReminderDao
import com.stackpointer.lists.data.entity.CompletionEntity
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.recurrence.RRule
import com.stackpointer.lists.recurrence.RRuleExpander
import kotlinx.coroutines.flow.Flow
import java.time.Duration
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
    val completedAt: Long?,
    /**
     * The newest completion-log id at the moment of the snapshot, or 0 if the
     * reminder had never been completed. Undo deletes anything logged after
     * this, which is what stops an undone swipe leaving a phantom entry on the
     * Completed screen.
     */
    val latestCompletionId: Long = 0
)

class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val completionDao: CompletionDao,
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
            completedAt = current.completedAt,
            latestCompletionId = completionDao.maxIdFor(id) ?: 0
        )
    }

    suspend fun restore(snapshot: ReminderUndoSnapshot) {
        val current = reminderDao.getById(snapshot.id) ?: return
        completionDao.deleteForReminderAfter(snapshot.id, snapshot.latestCompletionId)
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
                    val nextDueAt = next.toInstant().toEpochMilli()
                    // The occurrence that was just met only exists here: the
                    // update below overwrites dueAt with the next one.
                    logCompletion(current, nextDueAt)
                    reminderDao.update(current.copy(dueAt = nextDueAt))
                    alarms.requestSync()
                    return true
                }
            }
            // Not repeating, or the series has run out: the reminder is
            // completed for real and its due date stays where it is.
            if (current != null) logCompletion(current, current.dueAt)
        }
        if (!completed) {
            // Un-ticking is the same gesture as tapping Undo on the Completed
            // screen, so it has to take the history entry with it -- otherwise
            // the reminder is live again *and* still counted as done.
            completionDao.latestFor(id)?.let { completionDao.deleteById(it.id) }
        }
        reminderDao.setCompleted(
            id = id,
            completed = completed,
            completedAt = if (completed) Instant.now().toEpochMilli() else null
        )
        alarms.requestSync()
        return false
    }

    private suspend fun logCompletion(reminder: ReminderEntity, nextDueAt: Long?) {
        completionDao.insert(
            CompletionEntity(
                reminderId = reminder.id,
                completedAt = Instant.now().toEpochMilli(),
                dueAt = reminder.dueAt,
                wasAllDay = reminder.isAllDay,
                nextDueAt = nextDueAt
            )
        )
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

    // ---- Completed screen --------------------------------------------------

    fun observeCompletedEntries(limit: Int): Flow<List<CompletedEntryRow>> =
        completionDao.observeCompleted(limit)

    fun observeCompletedTotal(): Flow<Int> = completionDao.observeTotalCount()

    fun observeCompletedAtSince(from: Long): Flow<List<Long>> =
        completionDao.observeCompletedAtSince(from)

    fun observeCompletionsFor(reminderId: Long, limit: Int): Flow<List<CompletionEntity>> =
        completionDao.observeForReminder(reminderId, limit)

    fun observeCompletionCountFor(reminderId: Long): Flow<Int> =
        completionDao.observeCountForReminder(reminderId)

    /**
     * Puts one completed occurrence back.
     *
     * Only the *most recent* completion owns the reminder's current due date.
     * Undoing an older entry removes it from the history but leaves the due
     * date alone, because moving it back would step over occurrences that are
     * still recorded as done and re-alert for all of them.
     */
    suspend fun undoCompletion(completionId: Long) {
        val entry = completionDao.getById(completionId) ?: return
        val wasLatest = completionDao.latestFor(entry.reminderId)?.id == entry.id
        completionDao.deleteById(completionId)
        if (!wasLatest) return
        val reminder = reminderDao.getById(entry.reminderId) ?: return
        // The due date only goes back if it is still the one this completion
        // left behind. Someone who completed a repeating reminder and then
        // edited its next date has said something newer than this undo, and
        // silently overwriting that edit would lose real work.
        val untouched = reminder.dueAt == entry.nextDueAt
        reminderDao.update(
            reminder.copy(
                dueAt = if (untouched) entry.dueAt else reminder.dueAt,
                isAllDay = if (untouched) entry.wasAllDay else reminder.isAllDay,
                isCompleted = false,
                completedAt = null
            )
        )
        alarms.requestSync()
    }

    /**
     * Clears the Completed screen.
     *
     * Two different things are listed there, so two different things have to
     * happen. A reminder that is finished for good moves to the recycle bin --
     * not erased, because everywhere else in this app Delete means "recoverable
     * for 30 days". A *repeating* reminder is still running and is never
     * `isCompleted`, so it keeps going and only loses its history rows;
     * binning a live series behind a "delete all completed" menu item would
     * throw away a reminder the user still expects to fire.
     */
    suspend fun deleteAllCompleted(): DeleteAllCompletedResult {
        // History first: once the finished reminders are soft-deleted they no
        // longer count as active, and their rows would be left behind.
        val clearedHistory = completionDao.deleteAllForActiveReminders()
        val ids = reminderDao.completedIds()
        if (ids.isNotEmpty()) {
            reminderDao.setDeletedAtForIds(ids, Instant.now().toEpochMilli())
        }
        alarms.requestSync()
        return DeleteAllCompletedResult(binned = ids.size, historyCleared = clearedHistory)
    }

    // ---- Recycle bin -------------------------------------------------------

    fun observeDeleted(): Flow<List<ReminderEntity>> = reminderDao.observeDeleted()

    fun observeDeletedCount(): Flow<Int> = reminderDao.observeDeletedCount()

    suspend fun moveToBin(ids: List<Long>) {
        if (ids.isEmpty()) return
        reminderDao.setDeletedAtForIds(ids, Instant.now().toEpochMilli())
        alarms.requestSync()
    }

    suspend fun restoreFromBin(ids: List<Long>) {
        if (ids.isEmpty()) return
        reminderDao.setDeletedAtForIds(ids, null)
        alarms.requestSync()
    }

    suspend fun deleteForever(ids: List<Long>) {
        if (ids.isEmpty()) return
        reminderDao.hardDeleteIds(ids)
        alarms.requestSync()
    }

    /**
     * The retention sweep. Safe to call on every app start -- it's a single
     * indexed DELETE, and it only touches rows whose own deletion is older than
     * the window.
     */
    suspend fun purgeExpiredBin(): Int {
        val cutoff = Instant.now().minus(Duration.ofDays(BIN_RETENTION_DAYS)).toEpochMilli()
        val purged = reminderDao.purgeDeletedBefore(cutoff)
        if (purged > 0) alarms.requestSync()
        return purged
    }
}

/**
 * What "Delete all completed" actually did, so the confirmation snackbar can
 * say it rather than guess.
 */
data class DeleteAllCompletedResult(val binned: Int, val historyCleared: Int)

/** How long a deleted reminder stays recoverable. Stated on the bin screen. */
const val BIN_RETENTION_DAYS: Long = 30
