package com.stackpointer.lists.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.entity.ChecklistItemEntity
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.data.prefs.SearchHistoryStore
import com.stackpointer.lists.data.repository.AttachmentRepository
import com.stackpointer.lists.data.repository.ChecklistRepository
import com.stackpointer.lists.data.repository.ReminderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The four filter chips shown under the search field. All four filter for real;
 * [PHOTOS] matches against the ids the attachment table has rows for. */
enum class SearchFilter { OPEN, COMPLETED, CHECKLISTS, PHOTOS }

/** How a result matched the query, used to decide what context line to show under its title. */
private enum class MatchKind { TITLE, CHECKLIST, NOTE, OTHER }

data class SearchResultUiModel(
    val id: Long,
    val title: String,
    val isCompleted: Boolean,
    val completedText: String?,
    val checklistPreview: String?,
    val checklistDone: Int,
    val checklistTotal: Int,
    val hasChecklist: Boolean,
    val dueText: String?
)

data class SearchUiState(
    val query: String = "",
    val matchedQuery: String = "",
    // Matches the flow's own starting value. Left at null, the debounce meant
    // the Open chip rendered unselected for the first quarter-second of every
    // visit to Search.
    val activeFilter: SearchFilter? = SearchFilter.OPEN,
    val results: List<SearchResultUiModel> = emptyList(),
    val hasSearched: Boolean = false,
    val recentQueries: List<String> = emptyList()
)

class SearchViewModel(
    private val reminderRepository: ReminderRepository,
    private val checklistRepository: ChecklistRepository,
    private val searchHistoryStore: SearchHistoryStore,
    private val attachmentRepository: AttachmentRepository
) : ViewModel() {

    private val dueFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val completedFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    private val rawQuery = MutableStateFlow("")
    // Design S12: "Open is selected by default so completed items do not bury
    // live ones." It started as null, which meant a search for a word you use
    // often came back mostly things you had already finished.
    private val activeFilter = MutableStateFlow<SearchFilter?>(SearchFilter.OPEN)

    // Typing fires this on every keystroke, but the DB isn't hit until 250ms of
    // silence — otherwise "milk" is four separate queries, each racing the next.
    @OptIn(FlowPreview::class)
    private val debouncedQuery: Flow<String> = rawQuery
        .debounce(250)
        .map { it.trim() }

    // A blank LIKE '%%' matches every row, so a blank query short-circuits to an
    // empty result set instead of ever reaching the DAO.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val searchResults: Flow<List<ReminderEntity>> =
        debouncedQuery.flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else reminderRepository.search(query)
        }

    private val baseState = combine(
        rawQuery,
        debouncedQuery,
        searchResults,
        checklistRepository.observeAll(),
        // Rides along with the filter rather than taking a sixth slot: combine
        // tops out at five typed sources.
        activeFilter.combine(attachmentRepository.observeReminderIdsWithPhotos()) { filter, ids ->
            filter to ids.toSet()
        }
    ) { raw, matchedQuery, results, allChecklistItems, filterAndPhotos ->
        val (filter, reminderIdsWithPhotos) = filterAndPhotos
        buildState(raw, matchedQuery, results, allChecklistItems, filter, reminderIdsWithPhotos)
    }

    val uiState: StateFlow<SearchUiState> = baseState
        .combine(searchHistoryStore.recentQueries) { state, recents -> state.copy(recentQueries = recents) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState()
        )

    // Completing a repeating reminder rolls it to its next occurrence rather
    // than striking it off, which is invisible unless we say so.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    private fun buildState(
        raw: String,
        matchedQuery: String,
        results: List<ReminderEntity>,
        allChecklistItems: List<ChecklistItemEntity>,
        filter: SearchFilter?,
        reminderIdsWithPhotos: Set<Long>
    ): SearchUiState {
        val itemsByReminder = allChecklistItems.groupBy { it.reminderId }

        val models = results
            .filter { reminder ->
                when (filter) {
                    SearchFilter.OPEN -> !reminder.isCompleted
                    SearchFilter.COMPLETED -> reminder.isCompleted
                    SearchFilter.CHECKLISTS -> !itemsByReminder[reminder.id].isNullOrEmpty()
                    SearchFilter.PHOTOS -> reminder.id in reminderIdsWithPhotos
                    null -> true
                }
            }
            .map { reminder -> toResultUiModel(reminder, matchedQuery, itemsByReminder[reminder.id].orEmpty()) }

        return SearchUiState(
            query = raw,
            matchedQuery = matchedQuery,
            activeFilter = filter,
            results = models,
            hasSearched = matchedQuery.isNotBlank()
        )
    }

    private fun toResultUiModel(
        reminder: ReminderEntity,
        query: String,
        checklistItems: List<ChecklistItemEntity>
    ): SearchResultUiModel {
        val titleMatches = reminder.title.contains(query, ignoreCase = true)
        val matchingItem = checklistItems.firstOrNull { it.text.contains(query, ignoreCase = true) }
        val noteMatches = reminder.note?.contains(query, ignoreCase = true) == true

        val matchKind = when {
            titleMatches -> MatchKind.TITLE
            matchingItem != null -> MatchKind.CHECKLIST
            noteMatches -> MatchKind.NOTE
            else -> MatchKind.OTHER
        }

        val zone = ZoneId.systemDefault()
        val completedText = if (reminder.isCompleted) {
            val text = reminder.completedAt?.let {
                Instant.ofEpochMilli(it).atZone(zone).format(completedFormatter)
            }
            "Completed" + (text?.let { " $it" } ?: "")
        } else {
            null
        }

        val dueText = reminder.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(dueFormatter) }

        return SearchResultUiModel(
            id = reminder.id,
            title = reminder.title,
            isCompleted = reminder.isCompleted,
            completedText = completedText,
            checklistPreview = if (!reminder.isCompleted && matchKind == MatchKind.CHECKLIST) matchingItem?.text else null,
            checklistDone = checklistItems.count { it.isCompleted },
            checklistTotal = checklistItems.size,
            hasChecklist = checklistItems.isNotEmpty(),
            dueText = dueText.takeIf { !reminder.isCompleted && matchKind != MatchKind.CHECKLIST }
        )
    }

    fun onQueryChange(text: String) {
        rawQuery.value = text
    }

    fun onClearQuery() {
        rawQuery.value = ""
    }

    /** Called on the keyboard's Search action — this, not every keystroke, is what earns a
     * spot in "Recent searches". */
    fun submitSearch() {
        val query = rawQuery.value.trim()
        if (query.isBlank()) return
        viewModelScope.launch { searchHistoryStore.record(query) }
    }

    fun onRecentQuerySelected(query: String) {
        rawQuery.value = query
        viewModelScope.launch { searchHistoryStore.record(query) }
    }

    fun onFilterSelected(filter: SearchFilter) {
        activeFilter.value = if (activeFilter.value == filter) null else filter
    }

    fun removeRecentQuery(query: String) {
        viewModelScope.launch { searchHistoryStore.remove(query) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { searchHistoryStore.clear() }
    }

    fun toggleCompleted(id: Long, currentlyCompleted: Boolean) {
        viewModelScope.launch {
            val rolledForward = reminderRepository.setCompleted(id, !currentlyCompleted)
            if (rolledForward) _messages.tryEmit("Done — moved to the next occurrence")
        }
    }

    class Factory(
        private val reminderRepository: ReminderRepository,
        private val checklistRepository: ChecklistRepository,
        private val searchHistoryStore: SearchHistoryStore,
        private val attachmentRepository: AttachmentRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(
                reminderRepository,
                checklistRepository,
                searchHistoryStore,
                attachmentRepository
            ) as T
        }
    }
}
