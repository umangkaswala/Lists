package com.stackpointer.lists.data.repository

import com.stackpointer.lists.data.dao.ChecklistItemDao
import com.stackpointer.lists.data.entity.ChecklistItemEntity
import kotlinx.coroutines.flow.Flow

/** One checklist row as the Capture sheet holds it, before it has a database id. */
data class ChecklistItemDraft(val text: String, val isCompleted: Boolean = false)

class ChecklistRepository(private val checklistItemDao: ChecklistItemDao) {

    fun observeAll(): Flow<List<ChecklistItemEntity>> = checklistItemDao.getAll()

    fun observeForReminder(reminderId: Long): Flow<List<ChecklistItemEntity>> =
        checklistItemDao.getForReminder(reminderId)

    suspend fun setCompleted(itemId: Long, completed: Boolean) {
        checklistItemDao.setCompleted(itemId, completed)
    }

    /**
     * Replaces a reminder's whole checklist with [items].
     *
     * The caller passes each row's ticked state explicitly rather than the
     * repository re-deriving it from what's already stored: the editor is the
     * source of truth while the sheet is open, so inferring completion here
     * would silently undo a box the user just ticked (or unticked).
     */
    suspend fun replaceItems(reminderId: Long, items: List<ChecklistItemDraft>) {
        val entities = items
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() }
            .mapIndexed { index, draft ->
                ChecklistItemEntity(
                    reminderId = reminderId,
                    text = draft.text,
                    isCompleted = draft.isCompleted,
                    position = index
                )
            }
        checklistItemDao.replaceForReminder(reminderId, entities)
    }
}
