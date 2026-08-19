package com.stackpointer.lists.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.stackpointer.lists.data.entity.ReminderListEntity
import com.stackpointer.lists.data.entity.fallbackListFor
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderListDao {
    @Query("SELECT * FROM reminder_lists ORDER BY position ASC")
    fun getAll(): Flow<List<ReminderListEntity>>

    /** The same rows, read once. Needed inside a transaction, where a Flow can't go. */
    @Query("SELECT * FROM reminder_lists ORDER BY position ASC")
    suspend fun getAllOnce(): List<ReminderListEntity>

    @Query("SELECT * FROM reminder_lists WHERE id = :id")
    suspend fun getById(id: Long): ReminderListEntity?

    @Query("SELECT COUNT(*) FROM reminder_lists")
    suspend fun count(): Int

    @Insert
    suspend fun insert(list: ReminderListEntity): Long

    @Update
    suspend fun update(list: ReminderListEntity)

    @Update
    suspend fun updateAll(lists: List<ReminderListEntity>)

    @Delete
    suspend fun delete(list: ReminderListEntity)

    /**
     * Reassigns a list's reminders to another list and puts them in the recycle
     * bin. Declared here rather than on ReminderDao because it has to run in
     * the same transaction as the list row's own deletion, and Room's
     * `@Transaction` only spans one DAO.
     *
     * `COALESCE` leaves an already-binned reminder's timestamp alone: a row
     * that has been in the bin for 29 days should not earn another 30 out of
     * the list around it being deleted.
     */
    @Query(
        """
        UPDATE reminders
        SET listId = :targetListId,
            deletedAt = COALESCE(deletedAt, :deletedAt)
        WHERE listId = :sourceListId
        """
    )
    suspend fun binRemindersOfList(sourceListId: Long, targetListId: Long, deletedAt: Long): Int

    /**
     * Deletes a list without destroying what was in it. Returns how many
     * reminders were moved to the bin.
     *
     * `ReminderEntity`'s foreign key cascades, so the reminders have to be moved
     * off this list *before* its row goes -- and both halves have to be one
     * transaction, or a process death in between leaves reminders pointing at a
     * list that no longer exists.
     *
     * The destination is chosen *inside* the transaction for the same reason.
     * Picked outside and passed in, it could itself have been deleted by the
     * time the UPDATE ran, and the foreign key would reject the write.
     */
    @Transaction
    suspend fun deleteMovingRemindersToBin(list: ReminderListEntity, deletedAt: Long): Int {
        val target = fallbackListFor(getAllOnce(), list)
        if (target == null) {
            // The last list is going, so a restored reminder would have nowhere
            // to land. Nothing to do but let the cascade have them. The UI can't
            // reach this -- the default list has no delete button -- but the
            // database shouldn't depend on that.
            delete(list)
            return 0
        }
        val moved = binRemindersOfList(list.id, target.id, deletedAt)
        delete(list)
        return moved
    }
}
