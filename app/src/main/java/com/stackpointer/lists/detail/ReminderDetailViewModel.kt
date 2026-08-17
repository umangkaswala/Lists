package com.stackpointer.lists.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.entity.ChecklistItemEntity
import com.stackpointer.lists.data.repository.ChecklistRepository
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.data.repository.ReminderRepository
import com.stackpointer.lists.recurrence.RRule
import com.stackpointer.lists.recurrence.rruleSummary
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
    val repeatText: String = "None",
    val repeats: Boolean = false,
    val listName: String = "",
    val listColorArgb: Int = 0,
    val isImportant: Boolean = false,
    val isCompleted: Boolean = false,
    val checklist: List<ChecklistItemEntity> = emptyList()
)

class ReminderDetailViewModel(
    private val reminderId: Long,
    private val reminderRepository: ReminderRepository,
    listRepository: ListRepository,
    private val checklistRepository: ChecklistRepository
) : ViewModel() {

    private val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    val uiState: StateFlow<ReminderDetailUiState> = combine(
        reminderRepository.observeById(reminderId),
        listRepository.observeLists(),
        checklistRepository.observeForReminder(reminderId)
    ) { reminder, lists, checklist ->
        if (reminder == null) {
            ReminderDetailUiState(isLoading = false, found = false)
        } else {
            val list = lists.find { it.id == reminder.listId }
            val rule = RRule.parse(reminder.repeatRule)
            val anchorDate = (reminder.seriesStartAt ?: reminder.dueAt)?.let {
                Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            } ?: java.time.LocalDate.now()
            ReminderDetailUiState(
                isLoading = false,
                found = true,
                title = reminder.title,
                note = reminder.note,
                dueText = reminder.dueAt?.let {
                    Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(formatter)
                },
                repeatText = rule?.let { rruleSummary(it, anchorDate) } ?: "None",
                repeats = rule != null,
                listName = list?.name ?: "",
                listColorArgb = list?.colorArgb ?: 0,
                isImportant = reminder.isImportant,
                isCompleted = reminder.isCompleted,
                checklist = checklist
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReminderDetailUiState()
    )

    // Completing a repeating reminder rolls it to its next occurrence rather
    // than striking it off, which is invisible unless we say so.
    private val _messages = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: kotlinx.coroutines.flow.SharedFlow<String> = _messages

    fun toggleCompleted() {
        viewModelScope.launch {
            val rolledForward = reminderRepository.setCompleted(reminderId, !uiState.value.isCompleted)
            if (rolledForward) _messages.tryEmit("Done — moved to the next occurrence")
        }
    }

    fun toggleChecklistItem(itemId: Long, completed: Boolean) {
        viewModelScope.launch { checklistRepository.setCompleted(itemId, completed) }
    }

    fun toggleImportant() {
        viewModelScope.launch {
            reminderRepository.setImportant(reminderId, !uiState.value.isImportant)
        }
    }

    class Factory(
        private val reminderId: Long,
        private val reminderRepository: ReminderRepository,
        private val listRepository: ListRepository,
        private val checklistRepository: ChecklistRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReminderDetailViewModel(
                reminderId, reminderRepository, listRepository, checklistRepository
            ) as T
        }
    }
}
