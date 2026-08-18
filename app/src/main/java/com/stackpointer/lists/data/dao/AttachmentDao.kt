package com.stackpointer.lists.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.stackpointer.lists.data.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE reminderId = :reminderId ORDER BY createdAt ASC")
    fun observeForReminder(reminderId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE reminderId = :reminderId ORDER BY createdAt ASC")
    suspend fun getForReminder(reminderId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: Long): AttachmentEntity?

    /**
     * Every file name still referenced by a row. Used by the orphan sweep:
     * deleting a reminder cascades its attachment rows away, but the image
     * files on disk are not part of the database and would otherwise stay
     * forever.
     */
    @Query("SELECT fileName FROM attachments")
    suspend fun allFileNames(): List<String>

    @Insert
    suspend fun insert(attachment: AttachmentEntity): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
