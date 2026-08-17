package com.stackpointer.lists.capture

import com.stackpointer.lists.data.entity.ReminderListEntity
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.data.repository.ReminderRepository
import com.stackpointer.lists.parser.CaptureParser
import com.stackpointer.lists.recurrence.RRule
import com.stackpointer.lists.recurrence.RRuleExpander
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class CaptureMode { TYPING, WHEN, REPEAT }

data class CaptureUiState(
    val mode: CaptureMode = CaptureMode.TYPING,
    val title: String = "",
    val note: String? = null,
    val listId: Long? = null,
    val lists: List<ReminderListEntity> = emptyList(),
    val dueAt: Long? = null,
    val isAllDay: Boolean = false,
    val repeat: RRule? = null,
    /** True when the chips currently shown were read out of the typed text. */
    val parsedFromText: Boolean = false,
    /** Title with the recognised date/repeat words removed — what actually gets saved. */
    val cleanedTitle: String = "",
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val notFound: Boolean = false
) {
    val canSave: Boolean get() = title.isNotBlank() && !isSaving
    val listName: String get() = lists.find { it.id == listId }?.name ?: ""

    /** The date a repeat rule counts from, for summarising a rule that omits BYDAY. */
    val repeatAnchorDate: LocalDate
        get() = dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            ?: LocalDate.now()
}

// Deliberately not an androidx ViewModel: this is created directly with
// `remember(sheetKey)` in CaptureSheetContent, scoped to that composition. The
// ModalBottomSheet it lives in isn't a NavBackStackEntry destination, so
// there's no natural ViewModelStoreOwner to clear a real ViewModel from when
// the sheet closes — using one here leaked one retained instance per sheet open
// for the lifetime of the app process.
class CaptureViewModel(
    private val target: CaptureTarget,
    private val reminderRepository: ReminderRepository,
    private val listRepository: ListRepository,
    private val scope: CoroutineScope,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    // Once the user has set or cleared a field by hand, typing must stop
    // overwriting it — otherwise clearing the due chip on "Buy milk tomorrow"
    // would silently re-add it on the very next keystroke.
    private var dueOverridden = false
    private var repeatOverridden = false

    init {
        scope.launch {
            val lists = listRepository.observeLists().first()
            val defaultListId = lists.find { it.isDefault }?.id ?: lists.firstOrNull()?.id

            when (target) {
                is CaptureTarget.New -> {
                    _uiState.value = _uiState.value.copy(lists = lists, listId = defaultListId)
                    // Run the prefill through the parser too, so tapping an
                    // empty-state prompt like "Buy milk tomorrow morning"
                    // arrives with its chips already filled in.
                    updateTitle(target.prefillText)
                }
                is CaptureTarget.Edit -> {
                    val reminder = reminderRepository.observeById(target.reminderId).first()
                    if (reminder == null) {
                        _uiState.value = _uiState.value.copy(notFound = true)
                    } else {
                        // An existing reminder's fields are the user's own prior
                        // choices; re-parsing its title would be wrong.
                        dueOverridden = true
                        repeatOverridden = true
                        _uiState.value = _uiState.value.copy(
                            title = reminder.title,
                            cleanedTitle = reminder.title,
                            note = reminder.note,
                            lists = lists,
                            listId = reminder.listId,
                            dueAt = reminder.dueAt,
                            isAllDay = reminder.isAllDay,
                            repeat = RRule.parse(reminder.repeatRule),
                            isEditing = true
                        )
                    }
                }
            }
        }
    }

    fun updateTitle(text: String) {
        val state = _uiState.value
        if (dueOverridden && repeatOverridden) {
            _uiState.value = state.copy(title = text, cleanedTitle = text)
            return
        }

        val parsed = CaptureParser.parse(text, ZonedDateTime.now(zone))
        // Adopt the parse wholesale for any field the user hasn't taken over,
        // including when it now finds nothing: editing "Buy milk tomorrow" down
        // to "Buy milk" has to remove the chip it put there, not strand it.
        val dueAt = if (dueOverridden) state.dueAt else parsed.dueAt?.toInstant()?.toEpochMilli()
        val repeat = if (repeatOverridden) state.repeat else parsed.repeat
        val showedChipsFromText = (!dueOverridden && parsed.dueAt != null) ||
            (!repeatOverridden && parsed.repeat != null)

        _uiState.value = state.copy(
            title = text,
            cleanedTitle = if (showedChipsFromText) parsed.cleanedTitle else text,
            dueAt = dueAt,
            isAllDay = if (dueOverridden) state.isAllDay else parsed.isAllDay,
            repeat = repeat,
            parsedFromText = showedChipsFromText
        )
    }

    fun openWhen() {
        _uiState.value = _uiState.value.copy(mode = CaptureMode.WHEN)
    }

    // Repeat can be opened from the typing view's chip or from the When
    // sub-editor's row; closing it has to go back where it came from rather
    // than always landing on When.
    private var modeBeforeRepeat: CaptureMode = CaptureMode.WHEN

    fun openRepeat() {
        val state = _uiState.value
        modeBeforeRepeat = if (state.mode == CaptureMode.REPEAT) modeBeforeRepeat else state.mode
        _uiState.value = state.copy(mode = CaptureMode.REPEAT)
    }

    fun collapseToTyping() {
        _uiState.value = _uiState.value.copy(mode = CaptureMode.TYPING)
    }

    fun closeRepeat() {
        _uiState.value = _uiState.value.copy(mode = modeBeforeRepeat)
    }

    fun setAllDay(allDay: Boolean) {
        dueOverridden = true
        _uiState.value = _uiState.value.copy(isAllDay = allDay, parsedFromText = false)
    }

    fun setDueAt(epochMillis: Long?) {
        dueOverridden = true
        _uiState.value = _uiState.value.copy(dueAt = epochMillis, parsedFromText = false)
    }

    fun clearDue() {
        dueOverridden = true
        repeatOverridden = true
        // A repeat with no due date has nothing to recur from, so it goes too.
        _uiState.value = _uiState.value.copy(
            dueAt = null,
            isAllDay = false,
            repeat = null,
            parsedFromText = false
        )
    }

    fun setRepeat(rule: RRule?) {
        repeatOverridden = true
        _uiState.value = _uiState.value.copy(
            repeat = rule,
            mode = modeBeforeRepeat,
            parsedFromText = false
        )
    }

    fun clearRepeat() {
        repeatOverridden = true
        _uiState.value = _uiState.value.copy(repeat = null, parsedFromText = false)
    }

    /**
     * The first time [rule] fires from now, at the 9am default the parser also
     * uses. Returns null only if the rule's series has already ended.
     */
    private fun firstOccurrenceOf(rule: RRule): Long? {
        val now = ZonedDateTime.now(zone)
        // Anchor at 9am today so the series keeps a sensible time-of-day; asking
        // for the first occurrence strictly after now then skips today when 9am
        // has already gone, and skips days the rule doesn't match either way.
        val anchor = now.toLocalDate().atTime(9, 0).atZone(zone)
        return RRuleExpander.nextAfter(rule, anchor, now)?.toInstant()?.toEpochMilli()
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val listId = state.listId ?: return
        _uiState.value = state.copy(isSaving = true)

        // Fall back to the raw text if stripping the date words left nothing —
        // an untitled reminder is worse than a slightly wordy one.
        val titleToSave = state.cleanedTitle.trim().ifBlank { state.title.trim() }
        val rule = state.repeat
        // A repeat needs a due date to recur from. Rather than silently dropping
        // a rule the user just picked, derive the first occurrence from it.
        val dueToSave = state.dueAt ?: rule?.let { firstOccurrenceOf(it) }
        val ruleToSave = rule?.toRRuleString()?.takeIf { dueToSave != null }

        scope.launch {
            when (target) {
                is CaptureTarget.New -> {
                    reminderRepository.createReminder(
                        listId = listId,
                        title = titleToSave,
                        note = state.note,
                        dueAt = dueToSave,
                        isAllDay = state.isAllDay,
                        repeatRule = ruleToSave
                    )
                }
                is CaptureTarget.Edit -> {
                    reminderRepository.updateReminderFields(
                        id = target.reminderId,
                        title = titleToSave,
                        note = state.note,
                        listId = listId,
                        dueAt = dueToSave,
                        isAllDay = state.isAllDay,
                        repeatRule = ruleToSave
                    )
                }
            }
            _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
        }
    }
}
