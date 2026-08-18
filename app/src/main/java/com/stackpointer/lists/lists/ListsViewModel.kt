package com.stackpointer.lists.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.entity.ReminderListEntity
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.data.repository.ReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListsViewModel(
    private val listRepository: ListRepository,
    reminderRepository: ReminderRepository
) : ViewModel() {

    val lists: StateFlow<List<ReminderListEntity>> = listRepository.observeLists().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    /**
     * Open reminders per list. Design S15: "Support line carries the meaningful
     * state" — the row used to read a flat "List", which says nothing at all.
     *
     * Null until the first database emission, and deliberately not an empty
     * map: an empty map reads as "every list has nothing in it", so a full
     * app would flash "Nothing to do" under every name on the way in. The
     * screen shows no count at all while this is null.
     */
    val openCounts: StateFlow<Map<Long, Int>?> = reminderRepository.observeActive()
        .map { reminders ->
            reminders.filterNot { it.isCompleted }.groupingBy { it.listId }.eachCount()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun createList(name: String, colorArgb: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            listRepository.createList(name.trim(), colorArgb, lists.value.size)
        }
    }

    fun deleteList(list: ReminderListEntity) {
        viewModelScope.launch { listRepository.deleteList(list) }
    }

    fun reorder(newOrder: List<ReminderListEntity>) {
        viewModelScope.launch { listRepository.reorder(newOrder) }
    }

    class Factory(
        private val listRepository: ListRepository,
        private val reminderRepository: ReminderRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ListsViewModel(listRepository, reminderRepository) as T
        }
    }
}
