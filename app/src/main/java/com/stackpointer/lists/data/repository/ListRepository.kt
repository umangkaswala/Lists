package com.stackpointer.lists.data.repository

import com.stackpointer.lists.data.dao.ReminderListDao
import com.stackpointer.lists.data.entity.ReminderListEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class ListRepository(private val listDao: ReminderListDao) {
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
    }

    suspend fun reorder(lists: List<ReminderListEntity>) {
        listDao.updateAll(lists.mapIndexed { index, list -> list.copy(position = index) })
    }
}
