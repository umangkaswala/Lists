package com.stackpointer.lists.data.repository

import com.stackpointer.lists.data.dao.ReminderDao
import com.stackpointer.lists.data.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class ReminderRepository(private val reminderDao: ReminderDao) {
    fun observeActive(): Flow<List<ReminderEntity>> = reminderDao.getActive()

    fun observeById(id: Long): Flow<ReminderEntity?> = reminderDao.observeById(id)

    suspend fun createReminder(
        listId: Long,
        title: String,
        note: String? = null,
        dueAt: Long? = null,
        isAllDay: Boolean = false
    ): Long {
        return reminderDao.insert(
            ReminderEntity(
                listId = listId,
                title = title,
                note = note,
                dueAt = dueAt,
                isAllDay = isAllDay,
                createdAt = Instant.now().toEpochMilli()
            )
        )
    }

    suspend fun updateReminderFields(
        id: Long,
        title: String,
        note: String?,
        listId: Long,
        dueAt: Long?,
        isAllDay: Boolean
    ) {
        val current = reminderDao.getById(id) ?: return
        reminderDao.update(
            current.copy(
                title = title,
                note = note,
                listId = listId,
                dueAt = dueAt,
                isAllDay = isAllDay
            )
        )
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        reminderDao.setCompleted(
            id = id,
            completed = completed,
            completedAt = if (completed) Instant.now().toEpochMilli() else null
        )
    }

    suspend fun setImportant(id: Long, important: Boolean) {
        reminderDao.setImportant(id, important)
    }

    suspend fun softDelete(id: Long) {
        reminderDao.setDeletedAt(id, Instant.now().toEpochMilli())
    }
}
