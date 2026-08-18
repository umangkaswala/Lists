package com.stackpointer.lists.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.entity.ChecklistItemEntity
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.data.entity.ReminderListEntity
import com.stackpointer.lists.data.repository.ChecklistRepository
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.data.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HomeViewModel(
    private val reminderRepository: ReminderRepository,
    private val listRepository: ListRepository,
    checklistRepository: ChecklistRepository
) : ViewModel() {

    private val selectedListId = MutableStateFlow<Long?>(null)
    private val zone = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    val uiState: StateFlow<HomeUiState> = combine(
        reminderRepository.observeActive(),
        listRepository.observeLists(),
        selectedListId,
        checklistRepository.observeAll()
    ) { reminders, lists, selected, checklistItems ->
        buildUiState(reminders, lists, selected, checklistItems.groupBy { it.reminderId })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun selectList(listId: Long?) {
        selectedListId.value = if (selectedListId.value == listId) null else listId
    }

    fun toggleCompleted(reminderId: Long, completed: Boolean) {
        viewModelScope.launch { reminderRepository.setCompleted(reminderId, completed) }
    }

    fun toggleImportant(reminderId: Long, important: Boolean) {
        viewModelScope.launch { reminderRepository.setImportant(reminderId, important) }
    }

    private fun buildUiState(
        reminders: List<ReminderEntity>,
        lists: List<ReminderListEntity>,
        selectedListId: Long?,
        checklistByReminder: Map<Long, List<ChecklistItemEntity>>
    ): HomeUiState {
        val open = reminders.filter { !it.isCompleted }
        val now = Instant.now()
        val today = LocalDate.now(zone)

        fun dueDate(r: ReminderEntity): LocalDate? =
            r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

        val overdueCount = open.count { r -> r.dueAt != null && Instant.ofEpochMilli(r.dueAt) < now }
        val todayCount = open.count { r -> dueDate(r) == today && !(r.dueAt != null && Instant.ofEpochMilli(r.dueAt) < now) }

        val listTiles = lists.map { list ->
            ListTileUiModel(
                id = list.id,
                name = list.name,
                colorArgb = list.colorArgb,
                activeCount = open.count { it.listId == list.id }
            )
        }

        val filtered = if (selectedListId == null) open else open.filter { it.listId == selectedListId }

        val overdue = filtered
            .filter { it.dueAt != null && Instant.ofEpochMilli(it.dueAt) < now }
            .sortedBy { it.dueAt }
        val dueToday = filtered
            .filter { dueDate(it) == today && it !in overdue }
            .sortedBy { it.dueAt }
        val upcoming = filtered
            .filterNot { it in overdue || it in dueToday }
            .sortedWith(compareBy(nullsLast<Long>()) { it.dueAt })

        val sections = buildList {
            if (overdue.isNotEmpty()) add(ReminderSection("Overdue", isError = true, overdue.map { it.toCard(now, checklistByReminder) }))
            if (dueToday.isNotEmpty()) add(ReminderSection("Today", isError = false, dueToday.map { it.toCard(now, checklistByReminder) }))
            if (upcoming.isNotEmpty()) add(ReminderSection("Upcoming", isError = false, upcoming.map { it.toCard(now, checklistByReminder) }))
        }

        return HomeUiState(
            isLoading = false,
            overdueCount = overdueCount,
            todayCount = todayCount,
            listTiles = listTiles,
            selectedListId = selectedListId,
            sections = sections,
            hasNoReminders = reminders.isEmpty()
        )
    }

    private fun ReminderEntity.toCard(
        now: Instant,
        checklistByReminder: Map<Long, List<ChecklistItemEntity>>
    ): ReminderCardUiModel {
        val checklist = checklistByReminder[id].orEmpty()
        val meta = dueAt?.let { epoch ->
            val instant = Instant.ofEpochMilli(epoch)
            val date = instant.atZone(zone).toLocalDate()
            val label = when {
                instant < now -> "Overdue"
                date == LocalDate.now(zone) -> "Today"
                date == LocalDate.now(zone).plusDays(1) -> "Tomorrow"
                else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
            }
            if (isAllDay) label else "$label · ${instant.atZone(zone).toLocalTime().format(timeFormatter)}"
        }
        return ReminderCardUiModel(
            id = id,
            title = title,
            metaText = meta,
            isOverdue = dueAt != null && Instant.ofEpochMilli(dueAt) < now,
            isCompleted = isCompleted,
            isImportant = isImportant,
            repeats = repeatRule != null,
            checklistTotal = checklist.size,
            checklistDone = checklist.count { it.isCompleted }
        )
    }

    class Factory(
        private val reminderRepository: ReminderRepository,
        private val listRepository: ListRepository,
        private val checklistRepository: ChecklistRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(reminderRepository, listRepository, checklistRepository) as T
        }
    }
}
