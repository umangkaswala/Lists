package com.stackpointer.lists.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.stackpointer.lists.data.entity.ChecklistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistItemDao {
    @Query("SELECT * FROM checklist_items ORDER BY position ASC")
    fun getAll(): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE reminderId = :reminderId ORDER BY position ASC")
    fun getForReminder(reminderId: Long): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE reminderId = :reminderId ORDER BY position ASC")
    suspend fun getItemsOnce(reminderId: Long): List<ChecklistItemEntity>

    @Insert
    suspend fun insert(item: ChecklistItemEntity): Long

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Delete
    suspend fun delete(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE reminderId = :reminderId")
    suspend fun deleteForReminder(reminderId: Long)

    /**
     * Delete-then-insert as one atomic unit. Without the transaction, a save
     * cancelled part-way (the Capture sheet's scope dies when the sheet
     * closes) could commit the delete and lose every item.
     */
    @Transaction
    suspend fun replaceForReminder(reminderId: Long, items: List<ChecklistItemEntity>) {
        deleteForReminder(reminderId)
        items.forEach { insert(it) }
    }

    @Query("UPDATE checklist_items SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)
}
