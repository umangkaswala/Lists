package com.stackpointer.lists.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.entity.ChecklistItemEntity
import com.stackpointer.lists.data.repository.ChecklistRepository
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.completed.ON_TIME
import com.stackpointer.lists.completed.punctualityLabel
import com.stackpointer.lists.data.repository.ReminderRepository
import com.stackpointer.lists.recurrence.RRule
import com.stackpointer.lists.recurrence.rruleSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One line of Detail's history card. */
data class CompletionHistoryItem(
    val id: Long,
    val dateText: String,
    val punctualityText: String?,
    val wasOnTime: Boolean
)

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
    val checklist: List<ChecklistItemEntity> = emptyList(),
    val history: List<CompletionHistoryItem> = emptyList(),
    val historySummary: String = "",
    val totalCompletions: Int = 0
)

class ReminderDetailViewModel(
    private val reminderId: Long,
    private val reminderRepository: ReminderRepository,
    listRepository: ListRepository,
    private val checklistRepository: ChecklistRepository
) : ViewModel() {

    private val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val historyDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

    val uiState: StateFlow<ReminderDetailUiState> = combine(
        reminderRepository.observeById(reminderId),
        listRepository.observeLists(),
        checklistRepository.observeForReminder(reminderId),
        reminderRepository.observeCompletionsFor(reminderId, HISTORY_LIMIT),
        reminderRepository.observeCompletionCountFor(reminderId)
    ) { reminder, lists, checklist, completions, completionCount ->
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
                checklist = checklist,
                history = completions.map { completion ->
                    val label = punctualityLabel(
                        dueAt = completion.dueAt,
                        completedAt = completion.completedAt,
                        wasAllDay = completion.wasAllDay,
                        zone = zone
                    )
                    CompletionHistoryItem(
                        id = completion.id,
                        dateText = Instant.ofEpochMilli(completion.completedAt)
                            .atZone(zone).toLocalDate().format(historyDateFormat),
                        punctualityText = label,
                        wasOnTime = label == ON_TIME
                    )
                },
                historySummary = historySummary(completions.size, completionCount, completions),
                totalCompletions = completionCount
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

    /** Matches Today's swipe-to-snooze, so the two don't disagree. */
    fun snooze() {
        viewModelScope.launch {
            if (uiState.value.dueText == null) {
                _messages.tryEmit("This reminder has no time to snooze")
                return@launch
            }
            reminderRepository.snooze(reminderId, SNOOZE_MINUTES)
            _messages.tryEmit("Snoozed for $SNOOZE_MINUTES minutes")
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

    /**
     * "12 completed · 4 on time in a row". The streak counts back from the
     * newest completion and stops at the first late one — which is the whole
     * point of a streak, and why it can't be derived from the totals alone.
     */
    private fun historySummary(
        shown: Int,
        total: Int,
        completions: List<com.stackpointer.lists.data.entity.CompletionEntity>
    ): String {
        if (total == 0) return ""
        val streak = completions.takeWhile { completion ->
            punctualityLabel(completion.dueAt, completion.completedAt, completion.wasAllDay, zone) == ON_TIME
        }.size
        val completed = if (total == 1) "1 completed" else "$total completed"
        // A streak as long as the window we loaded might really be longer, so
        // it isn't claimed as a number the app can't stand behind.
        return when {
            streak == 0 -> completed
            streak >= shown && total > shown -> "$completed · on time every time lately"
            streak == 1 -> "$completed · last one on time"
            else -> "$completed · $streak on time in a row"
        }
    }

    private companion object {
        const val SNOOZE_MINUTES = 30L

        /** Enough rows to show a streak without needing paging of its own. */
        const val HISTORY_LIMIT = 8
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
