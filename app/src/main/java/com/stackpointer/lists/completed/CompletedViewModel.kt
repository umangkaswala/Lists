package com.stackpointer.lists.completed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.dao.CompletedEntryRow
import com.stackpointer.lists.data.repository.DeleteAllCompletedResult
import com.stackpointer.lists.data.repository.ReminderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** One bar of the seven-day chart. */
data class CompletedDayBar(val label: String, val count: Int, val isToday: Boolean)

data class CompletedEntryUiModel(
    val completionId: Long,
    val reminderId: Long,
    val title: String,
    val meta: String,
    /** Kept so bulk undo can work newest-first; see [CompletedViewModel.undoAll]. */
    val completedAt: Long,
    /** The reminder behind this entry is a live repeating series. */
    val isRepeating: Boolean
)

data class CompletedGroup(val label: String, val entries: List<CompletedEntryUiModel>)

data class CompletedUiState(
    val isLoading: Boolean = true,
    val total: Int = 0,
    val thisWeek: Int = 0,
    val chart: List<CompletedDayBar> = emptyList(),
    val groups: List<CompletedGroup> = emptyList(),
    val canLoadMore: Boolean = false
) {
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
    // "311 reminders" would be wrong: this counts completions, and one daily
    // reminder ticked off all month is 30 of them, not 30 reminders.
    val subtitle: String get() = "$total completed · $thisWeek this week"
}

internal fun plural(count: Int, noun: String): String =
    if (count == 1) "1 $noun" else "$count ${noun}s"

/** The design pages the list; this is one page. */
private const val PAGE_SIZE = 50

class CompletedViewModel(
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    private val limit = MutableStateFlow(PAGE_SIZE)

    // Anchored once per screen open rather than re-read inside the mapping: the
    // chart's seven columns and the Today / Last 7 days group boundaries have to
    // agree with each other, and re-reading the clock per emission could put a
    // row under "Today" after the chart had already rolled over to a new day.
    private val today: LocalDate = LocalDate.now(zone)
    private val weekStart: Long = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CompletedUiState> = combine(
        limit.flatMapLatest { reminderRepository.observeCompletedEntries(it) },
        reminderRepository.observeCompletedTotal(),
        reminderRepository.observeCompletedAtSince(weekStart),
        limit
    ) { entries, total, weekTimestamps, currentLimit ->
        CompletedUiState(
            isLoading = false,
            total = total,
            thisWeek = weekTimestamps.size,
            chart = buildChart(weekTimestamps),
            groups = buildGroups(entries),
            // A full page back means there is probably another one behind it.
            // Being wrong costs one tap that adds nothing, not a missing row.
            canLoadMore = entries.size >= currentLimit
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompletedUiState())

    fun loadMore() {
        limit.value += PAGE_SIZE
    }

    fun undo(completionId: Long) {
        viewModelScope.launch { reminderRepository.undoCompletion(completionId) }
    }

    /**
     * Undoes newest-first. Order matters: only a reminder's most recent
     * completion is allowed to move its due date back (see
     * ReminderRepository.undoCompletion), so undoing an older sibling first
     * would leave the newer one owning a due date that no longer has history
     * behind it.
     */
    fun undoAll(entries: List<CompletedEntryUiModel>) {
        viewModelScope.launch {
            entries.sortedByDescending { it.completedAt }
                .forEach { reminderRepository.undoCompletion(it.completionId) }
        }
    }

    /** Sends the reminders behind these entries to the recycle bin. */
    fun binEntries(entries: List<CompletedEntryUiModel>) {
        viewModelScope.launch {
            reminderRepository.moveToBin(entries.map { it.reminderId }.distinct())
        }
    }

    fun deleteAllCompleted(onDone: (DeleteAllCompletedResult) -> Unit) {
        viewModelScope.launch { onDone(reminderRepository.deleteAllCompleted()) }
    }

    private fun buildChart(timestamps: List<Long>): List<CompletedDayBar> {
        val counts = timestamps
            .groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .eachCount()
        return (6 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            CompletedDayBar(
                // Single letter, as the design labels them: M T W T F S S.
                label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                count = counts[date] ?: 0,
                isToday = back == 0
            )
        }
    }

    private fun buildGroups(entries: List<CompletedEntryRow>): List<CompletedGroup> {
        if (entries.isEmpty()) return emptyList()
        val sevenDaysAgo = today.minusDays(6)
        // LinkedHashMap: rows arrive newest-first, so insertion order is already
        // the order the groups belong in.
        val grouped = LinkedHashMap<String, MutableList<CompletedEntryUiModel>>()
        entries.forEach { row ->
            val date = Instant.ofEpochMilli(row.completedAt).atZone(zone).toLocalDate()
            val label = when {
                date == today -> "Today"
                !date.isBefore(sevenDaysAgo) -> "Last 7 days"
                else -> date.format(monthFormat)
            }
            grouped.getOrPut(label) { mutableListOf() }.add(toUiModel(row, date))
        }
        return grouped.map { (label, items) -> CompletedGroup(label, items) }
    }

    private fun toUiModel(row: CompletedEntryRow, completedOn: LocalDate): CompletedEntryUiModel {
        val done = Instant.ofEpochMilli(row.completedAt).atZone(zone)
        val meta = if (completedOn == today) {
            // Today's rows carry both clock times, as the design shows them:
            // "Due 7:00 am · done 7:12 am".
            val doneText = "done " + done.toLocalTime().format(timeFormat).lowercase(Locale.ENGLISH)
            val dueText = row.dueAt
                ?.takeIf { !row.wasAllDay }
                ?.let { Instant.ofEpochMilli(it).atZone(zone) }
                ?.let { "Due " + it.toLocalTime().format(timeFormat).lowercase(Locale.ENGLISH) }
            if (dueText == null) doneText.replaceFirstChar { it.uppercase() } else "$dueText · $doneText"
        } else {
            val day = completedOn.format(dayFormat)
            val punctuality = punctualityLabel(row.dueAt, row.completedAt, row.wasAllDay, zone)
            if (punctuality == null) day else "$day · $punctuality"
        }
        return CompletedEntryUiModel(
            completionId = row.completionId,
            reminderId = row.reminderId,
            title = row.title,
            meta = meta,
            completedAt = row.completedAt,
            isRepeating = row.isRepeating
        )
    }

    class Factory(private val reminderRepository: ReminderRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CompletedViewModel(reminderRepository) as T
        }
    }
}
