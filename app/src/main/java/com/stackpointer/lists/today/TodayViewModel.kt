package com.stackpointer.lists.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.entity.ChecklistItemEntity
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.data.repository.ChecklistRepository
import com.stackpointer.lists.data.repository.ReminderRepository
import com.stackpointer.lists.data.repository.ReminderUndoSnapshot
import com.stackpointer.lists.recurrence.RRule
import com.stackpointer.lists.recurrence.rruleShortLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class TodaySectionKind { OVERDUE, LATER_TODAY, COMPLETED }

data class TodayReminderCardUiModel(
    val id: Long,
    val title: String,
    val timeText: String?,
    val repeatLabel: String?,
    val isImportant: Boolean,
    val checklistDone: Int = 0,
    val checklistTotal: Int = 0,
    val isCompleted: Boolean = false,
    val completedTimeText: String? = null
) {
    val hasChecklist: Boolean get() = checklistTotal > 0
}

data class TodaySection(
    val kind: TodaySectionKind,
    val label: String,
    val reminders: List<TodayReminderCardUiModel>
)

data class TodayUiState(
    val isLoading: Boolean = true,
    val subtitle: String = "",
    val sections: List<TodaySection> = emptyList()
) {
    val isEmpty: Boolean get() = !isLoading && sections.isEmpty()
}

class TodayViewModel(
    private val reminderRepository: ReminderRepository,
    private val checklistRepository: ChecklistRepository
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    // English regardless of device locale, matching the rest of the codebase
    // (see RRuleText.kt) so strings/tests don't depend on device settings.
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

    val uiState: StateFlow<TodayUiState> = combine(
        reminderRepository.observeActive(),
        checklistRepository.observeAll()
    ) { reminders, checklistItems ->
        buildUiState(reminders, checklistItems)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodayUiState()
    )

    /**
     * Plain suspend wrappers (not launched on [viewModelScope]) so the screen
     * can sequence snapshot -> action -> snackbar -> maybe-undo itself and
     * keep that sequence alive for as long as the screen is composed, even if
     * the specific row that started it is removed from the list mid-flight
     * (e.g. a swipe-completed item rolls out of today's window).
     */
    suspend fun snapshotFor(id: Long): ReminderUndoSnapshot? = reminderRepository.snapshotFor(id)

    /** Returns true if a repeating reminder rolled forward instead of completing. */
    suspend fun completeReminder(id: Long): Boolean = reminderRepository.setCompleted(id, true)

    suspend fun snoozeReminder(id: Long) = reminderRepository.snooze(id, 30)

    suspend fun undo(snapshot: ReminderUndoSnapshot) = reminderRepository.restore(snapshot)

    private fun buildUiState(
        reminders: List<ReminderEntity>,
        checklistItems: List<ChecklistItemEntity>
    ): TodayUiState {
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        val startOfToday = today.atStartOfDay(zone).toInstant()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant()
        val checklistByReminder = checklistItems.groupBy { it.reminderId }

        // An all-day reminder isn't late until the day is over. Comparing its
        // stored instant (midnight, or whatever time it was created at) to
        // `now` would drop every all-day item into the red Overdue section
        // from the first minute of the day it's due.
        fun deadline(r: ReminderEntity): Instant? = r.dueAt?.let { due ->
            if (r.isAllDay) {
                Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                    .plusDays(1).atStartOfDay(zone).toInstant()
            } else {
                Instant.ofEpochMilli(due)
            }
        }

        val overdue = reminders
            .filter { !it.isCompleted && deadline(it)?.let { d -> d < now } == true }
            .sortedBy { it.dueAt }

        val laterToday = reminders
            .filter { r ->
                val deadline = deadline(r)
                !r.isCompleted && r.dueAt != null && deadline != null &&
                    deadline >= now && Instant.ofEpochMilli(r.dueAt) < startOfTomorrow
            }
            .sortedBy { it.dueAt }

        val completedToday = reminders
            .filter { r ->
                r.isCompleted && r.completedAt != null &&
                    Instant.ofEpochMilli(r.completedAt).let { it >= startOfToday && it < startOfTomorrow }
            }
            .sortedByDescending { it.completedAt }

        val total = overdue.size + laterToday.size + completedToday.size

        val sections = buildList {
            if (overdue.isNotEmpty()) {
                add(
                    TodaySection(
                        TodaySectionKind.OVERDUE,
                        "Overdue",
                        overdue.map { it.toCard(checklistByReminder, now) }
                    )
                )
            }
            if (laterToday.isNotEmpty()) {
                add(
                    TodaySection(
                        TodaySectionKind.LATER_TODAY,
                        "Later today",
                        laterToday.map { it.toCard(checklistByReminder, now) }
                    )
                )
            }
            if (completedToday.isNotEmpty()) {
                add(
                    TodaySection(
                        TodaySectionKind.COMPLETED,
                        "Completed today",
                        completedToday.map { it.toCard(checklistByReminder, now) }
                    )
                )
            }
        }

        val subtitle = "${today.format(dateFormatter)} · $total reminder${if (total == 1) "" else "s"}"

        return TodayUiState(isLoading = false, subtitle = subtitle, sections = sections)
    }

    private fun ReminderEntity.toCard(
        checklistByReminder: Map<Long, List<ChecklistItemEntity>>,
        now: Instant
    ): TodayReminderCardUiModel {
        val timeText = if (isAllDay) null else dueAt?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalTime().format(timeFormatter)
        }
        val rule = RRule.parse(repeatRule)
        val repeatLabel = rule?.let {
            val anchorMillis = seriesStartAt ?: dueAt ?: now.toEpochMilli()
            val anchorDate = Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate()
            rruleShortLabel(it, anchorDate)
        }
        val items = checklistByReminder[id].orEmpty()
        val completedTimeText = if (isCompleted) {
            completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime().format(timeFormatter) }
        } else {
            null
        }

        return TodayReminderCardUiModel(
            id = id,
            title = title,
            timeText = timeText,
            repeatLabel = repeatLabel,
            isImportant = isImportant,
            checklistDone = items.count { it.isCompleted },
            checklistTotal = items.size,
            isCompleted = isCompleted,
            completedTimeText = completedTimeText
        )
    }

    class Factory(
        private val reminderRepository: ReminderRepository,
        private val checklistRepository: ChecklistRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TodayViewModel(reminderRepository, checklistRepository) as T
        }
    }
}
