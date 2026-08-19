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

    /**
     * Deletes the list and sweeps its reminders into the recycle bin, rather
     * than destroying them.
     *
     * The foreign key cascades, so the obvious one-liner (`listDao.delete`)
     * hard-deleted every reminder in the list -- and, through a second cascade,
     * their completion history too -- while the bin two taps away promises that
     * deleted reminders are kept. This was the only delete in the app with no
     * way back, and the confirmation dialog didn't say so.
     *
     * Returns how many reminders were moved, or 0 if there was nowhere to move
     * them.
     */
    suspend fun deleteList(list: ReminderListEntity): Int {
        val moved = listDao.deleteMovingRemindersToBin(list, Instant.now().toEpochMilli())
        // Alarms and geofences for the binned reminders would otherwise stay
        // registered and fire for rows the user can no longer see.
        alarms.requestSync()
        return moved
    }

    suspend fun reorder(lists: List<ReminderListEntity>) {
        listDao.updateAll(lists.mapIndexed { index, list -> list.copy(position = index) })
    }
}

