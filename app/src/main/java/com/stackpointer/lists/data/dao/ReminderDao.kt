package com.stackpointer.lists.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.stackpointer.lists.data.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL ORDER BY dueAt ASC")
    fun getActive(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observeById(id: Long): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT COUNT(*) FROM reminders WHERE deletedAt IS NULL")
    suspend fun countActive(): Int

    /**
     * Every row, unfiltered — completed and soft-deleted included.
     *
     * The alarm scheduler needs the rows it must *cancel* as much as the ones
     * it must schedule, and filtering here would leave a completed reminder's
     * alarm registered forever.
     */
    @Query("SELECT * FROM reminders")
    suspend fun getAllForScheduling(): List<ReminderEntity>

    /**
     * Full-text-ish search across a reminder's title, note and checklist item
     * text. LIKE with a leading wildcard can't use an index, but this table is
     * a personal reminder list — hundreds of rows, not millions — so the
     * simplicity is worth more than an FTS4 virtual table and its triggers.
     *
     * The DISTINCT matters: a reminder whose title *and* two checklist items
     * all match would otherwise come back three times.
     *
     * Callers must pass the query through [ReminderRepository.escapeForLike] —
     * a literal `%` or `_` typed by the user is a LIKE wildcard otherwise, and
     * searching "50%" would return every reminder in the database.
     */
    @Query(
        "SELECT DISTINCT r.* FROM reminders r " +
            "LEFT JOIN checklist_items c ON c.reminderId = r.id " +
            "WHERE r.deletedAt IS NULL AND (" +
            "  r.title LIKE '%' || :query || '%' ESCAPE '\\' OR " +
            "  r.note LIKE '%' || :query || '%' ESCAPE '\\' OR " +
            "  c.text LIKE '%' || :query || '%' ESCAPE '\\') " +
            "ORDER BY r.isCompleted ASC, r.dueAt IS NULL, r.dueAt ASC"
    )
    fun search(query: String): Flow<List<ReminderEntity>>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query(
        "UPDATE reminders SET isCompleted = :completed, " +
            "completedAt = :completedAt WHERE id = :id"
    )
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?)

    @Query("UPDATE reminders SET isImportant = :important WHERE id = :id")
    suspend fun setImportant(id: Long, important: Boolean)

    @Query("UPDATE reminders SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: Long?)
}
