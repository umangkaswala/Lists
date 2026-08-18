package com.stackpointer.lists.data.repository

import com.stackpointer.lists.data.dao.ReminderListDao
import com.stackpointer.lists.data.entity.ReminderListEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class ListRepository(
    private val listDao: ReminderListDao,
    private val alarms: ReminderAlarms = ReminderAlarms.None
) {
    fun observeLists(): Flow<List<ReminderListEntity>> = listDao.getAll()

    suspend fun createList(name: String, colorArgb: Int, position: Int): Long {
        return listDao.insert(
            ReminderListEntity(
                name = name,
                colorArgb = colorArgb,
                position = position,
                createdAt = Instant.now().toEpochMilli()
            )
        )
    }

    suspend fun renameList(list: ReminderListEntity, name: String) {
        listDao.update(list.copy(name = name))
    }

    suspend fun deleteList(list: ReminderListEntity) {
        listDao.delete(list)
        // ReminderEntity's foreign key cascades, so this hard-deletes every
        // reminder in the list. Their alarms would otherwise stay registered
        // and fire for rows that no longer exist.
        alarms.requestSync()
    }

    suspend fun reorder(lists: List<ReminderListEntity>) {
        listDao.updateAll(lists.mapIndexed { index, list -> list.copy(position = index) })
    }
}
