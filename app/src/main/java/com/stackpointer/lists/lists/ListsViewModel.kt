package com.stackpointer.lists.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.entity.ReminderListEntity
import com.stackpointer.lists.data.repository.ListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ListsViewModel(private val listRepository: ListRepository) : ViewModel() {

    val lists: StateFlow<List<ReminderListEntity>> = listRepository.observeLists().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
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

    class Factory(private val listRepository: ListRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ListsViewModel(listRepository) as T
        }
    }
}
