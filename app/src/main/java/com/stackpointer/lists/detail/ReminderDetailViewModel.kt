package com.stackpointer.lists.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.data.repository.ReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class ReminderDetailUiState(
    val isLoading: Boolean = true,
    val found: Boolean = true,
    val title: String = "",
    val note: String? = null,
    val dueText: String? = null,
    val listName: String = "",
    val listColorArgb: Int = 0,
    val isImportant: Boolean = false,
    val isCompleted: Boolean = false
)

class ReminderDetailViewModel(
    private val reminderId: Long,
    private val reminderRepository: ReminderRepository,
    listRepository: ListRepository
) : ViewModel() {

    private val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    val uiState: StateFlow<ReminderDetailUiState> = combine(
        reminderRepository.observeById(reminderId),
        listRepository.observeLists()
    ) { reminder, lists ->
        if (reminder == null) {
            ReminderDetailUiState(isLoading = false, found = false)
        } else {
            val list = lists.find { it.id == reminder.listId }
            ReminderDetailUiState(
                isLoading = false,
                found = true,
                title = reminder.title,
                note = reminder.note,
                dueText = reminder.dueAt?.let {
                    Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(formatter)
                },
                listName = list?.name ?: "",
                listColorArgb = list?.colorArgb ?: 0,
                isImportant = reminder.isImportant,
                isCompleted = reminder.isCompleted
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReminderDetailUiState()
    )

    fun toggleCompleted() {
        viewModelScope.launch {
            reminderRepository.setCompleted(reminderId, !uiState.value.isCompleted)
        }
    }

    fun toggleImportant() {
        viewModelScope.launch {
            reminderRepository.setImportant(reminderId, !uiState.value.isImportant)
        }
    }

    class Factory(
        private val reminderId: Long,
        private val reminderRepository: ReminderRepository,
        private val listRepository: ListRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReminderDetailViewModel(reminderId, reminderRepository, listRepository) as T
        }
    }
}
