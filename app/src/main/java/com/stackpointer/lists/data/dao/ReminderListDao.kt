package com.stackpointer.lists.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.stackpointer.lists.data.entity.ReminderListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderListDao {
    @Query("SELECT * FROM reminder_lists ORDER BY position ASC")
    fun getAll(): Flow<List<ReminderListEntity>>

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
}
