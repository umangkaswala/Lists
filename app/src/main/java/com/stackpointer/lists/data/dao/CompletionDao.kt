package com.stackpointer.lists.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.stackpointer.lists.data.entity.CompletionEntity
import kotlinx.coroutines.flow.Flow

/**
 * One completed occurrence as the Completed screen needs it: the log row
 * joined to the reminder it belongs to, so the list can show a title without
 * a second query per row.
 */
data class CompletedEntryRow(
    val completionId: Long,
    val reminderId: Long,
    val title: String,
    val completedAt: Long,
    val dueAt: Long?,
    val wasAllDay: Boolean,
    val isRepeating: Boolean
)

@Dao
interface CompletionDao {
    @Insert
    suspend fun insert(completion: CompletionEntity): Long

    /**
     * Soft-deleted reminders are excluded: they're sitting in the recycle bin,
     * and showing their history here would offer an Undo that puts an occurrence
     * back onto a reminder the user has already thrown away.
     */
    @Query(
        "SELECT c.id AS completionId, c.reminderId AS reminderId, r.title AS title, " +
            "c.completedAt AS completedAt, c.dueAt AS dueAt, c.wasAllDay AS wasAllDay, " +
            "(r.repeatRule IS NOT NULL) AS isRepeating " +
            "FROM completions c JOIN reminders r ON r.id = c.reminderId " +
            "WHERE r.deletedAt IS NULL " +
            "ORDER BY c.completedAt DESC LIMIT :limit"
    )
    fun observeCompleted(limit: Int): Flow<List<CompletedEntryRow>>

    @Query(
        "SELECT COUNT(*) FROM completions c JOIN reminders r ON r.id = c.reminderId " +
            "WHERE r.deletedAt IS NULL"
    )
    fun observeTotalCount(): Flow<Int>

    /**
     * Raw timestamps rather than a SQL `GROUP BY` on a formatted date: SQLite's
     * date functions work in UTC or the *system* zone, and bucketing days is
     * exactly where that difference shows up as a bar in the wrong column.
     * Kotlin does the bucketing with a real [java.time.ZoneId].
     */
    @Query(
        "SELECT c.completedAt FROM completions c JOIN reminders r ON r.id = c.reminderId " +
            "WHERE r.deletedAt IS NULL AND c.completedAt >= :from"
    )
    fun observeCompletedAtSince(from: Long): Flow<List<Long>>

    @Query("SELECT * FROM completions WHERE reminderId = :reminderId ORDER BY completedAt DESC LIMIT :limit")
    fun observeForReminder(reminderId: Long, limit: Int): Flow<List<CompletionEntity>>

    @Query("SELECT COUNT(*) FROM completions WHERE reminderId = :reminderId")
    fun observeCountForReminder(reminderId: Long): Flow<Int>

    @Query("SELECT * FROM completions WHERE id = :id")
    suspend fun getById(id: Long): CompletionEntity?

    @Query("SELECT * FROM completions WHERE reminderId = :reminderId ORDER BY completedAt DESC LIMIT 1")
    suspend fun latestFor(reminderId: Long): CompletionEntity?

    /**
     * The undo watermark, deliberately by id rather than by completedAt: undo
     * deletes rows with `id >` this, so the two have to be measured the same
     * way. A device clock that steps backwards between two completions makes
     * "newest by time" and "newest by id" disagree, and undo would then delete
     * an entry it never created.
     */
    @Query("SELECT MAX(id) FROM completions WHERE reminderId = :reminderId")
    suspend fun maxIdFor(reminderId: Long): Long?

    /**
     * Clears the history the Completed screen actually shows. Rows belonging to
     * reminders already in the recycle bin are left alone -- they aren't on
     * that screen, and they come back with the reminder if it's restored.
     */
    @Query(
        "DELETE FROM completions WHERE reminderId IN " +
            "(SELECT id FROM reminders WHERE deletedAt IS NULL)"
    )
    suspend fun deleteAllForActiveReminders(): Int

    @Query("DELETE FROM completions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Used by undo: removes whatever completions a swipe created since the
     * snapshot was taken, without needing to know how many there were.
     */
    @Query("DELETE FROM completions WHERE reminderId = :reminderId AND id > :afterId")
    suspend fun deleteForReminderAfter(reminderId: Long, afterId: Long)

    @Query("DELETE FROM completions WHERE reminderId IN (:reminderIds)")
    suspend fun deleteForReminders(reminderIds: List<Long>)
}
