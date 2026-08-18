package com.stackpointer.lists.capture

import com.stackpointer.lists.data.entity.ReminderListEntity
import com.stackpointer.lists.data.repository.ChecklistItemDraft
import com.stackpointer.lists.data.entity.PlaceEntity
import com.stackpointer.lists.data.repository.ChecklistRepository
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.data.repository.PlaceRepository
import com.stackpointer.lists.data.repository.PlaceTriggerDraft
import com.stackpointer.lists.data.repository.ReminderRepository
import com.stackpointer.lists.parser.CaptureParser
import com.stackpointer.lists.places.PlaceTrigger
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
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

enum class CaptureMode { TYPING, WHEN, WHERE, REPEAT, LIST }

data class CaptureUiState(
    val mode: CaptureMode = CaptureMode.TYPING,
    val title: String = "",
    val note: String? = null,
    val listId: Long? = null,
    val lists: List<ReminderListEntity> = emptyList(),
    val checklist: List<ChecklistItemDraft> = emptyList(),
    val showChecklist: Boolean = false,
    val dueAt: Long? = null,
    val isAllDay: Boolean = false,
    val repeat: RRule? = null,
    // ---- Place trigger (design S08) ----------------------------------------
    val places: List<PlaceEntity> = emptyList(),
    val placeId: Long? = null,
    val placeTrigger: PlaceTrigger = PlaceTrigger.ARRIVE,
    val placeRadiusMeters: Int = DEFAULT_PLACE_RADIUS_METERS,
    val placeWindowStartMinute: Int? = null,
    val placeWindowEndMinute: Int? = null,
    val placeWindowDays: String? = null,
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
    val listColorArgb: Int get() = lists.find { it.id == listId }?.colorArgb ?: 0

    val place: PlaceEntity? get() = places.find { it.id == placeId }
    val hasPlace: Boolean get() = place != null

    /** "Arrive at Home", as the summary chip shows it. */
    val placeChipLabel: String?
        get() = place?.let { p ->
            val verb = if (placeTrigger == PlaceTrigger.ARRIVE) "Arrive at" else "Leave"
            "$verb ${p.name}"
        }


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
/**
 * Google's geofencing is unreliable below about 100 m, and 200 m is far enough
 * that a phone parked in a driveway still counts as home. It is the middle stop
 * on the design's slider.
 */
const val DEFAULT_PLACE_RADIUS_METERS = 200

/** The time of day a reminder lands on when only a date was chosen. */
private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

/**
 * The time of day to invent when the user chose a date but never chose a time.
 *
 * [DEFAULT_TIME], unless that has already gone by on the date in question — an
 * invented time in the past is worse than no time at all, because AlarmPlanner
 * drops any trigger that has already passed and the reminder then never alerts.
 */
private fun defaultTimeOn(date: LocalDate, zone: ZoneId): LocalTime {
    val now = ZonedDateTime.now(zone)
    if (date != now.toLocalDate() || now.toLocalTime() < DEFAULT_TIME) return DEFAULT_TIME
    val nextHour = now.plusHours(1).truncatedTo(ChronoUnit.HOURS)
    // Late enough in the evening that the next whole hour is tomorrow: keep the
    // date the user picked and settle for the last minute of it.
    return if (nextHour.toLocalDate() == now.toLocalDate()) nextHour.toLocalTime() else LocalTime.of(23, 59)
}

class CaptureViewModel(
    private val target: CaptureTarget,
    private val reminderRepository: ReminderRepository,
    private val listRepository: ListRepository,
    private val checklistRepository: ChecklistRepository,
    private val placeRepository: PlaceRepository,
    private val scope: CoroutineScope,
    /**
     * For writes that must survive the sheet closing. [scope] is the sheet's
     * own `rememberCoroutineScope()` and dies with the composition — the trap
     * CLAUDE.md records from Phase 4 — and a place is saved the moment it is
     * picked, not on Send, so it needs a scope that outlives the sheet.
     */
    private val appScope: CoroutineScope,
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
            val places = placeRepository.observePlaces().first()

            when (target) {
                is CaptureTarget.New -> {
                    _uiState.value = _uiState.value.copy(
                        lists = lists,
                        listId = defaultListId,
                        places = places
                    )
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
                        val existingChecklist = checklistRepository
                            .observeForReminder(target.reminderId).first()
                            .map { ChecklistItemDraft(it.text, it.isCompleted) }
                        _uiState.value = _uiState.value.copy(
                            title = reminder.title,
                            cleanedTitle = reminder.title,
                            note = reminder.note,
                            lists = lists,
                            listId = reminder.listId,
                            dueAt = reminder.dueAt,
                            isAllDay = reminder.isAllDay,
                            repeat = RRule.parse(reminder.repeatRule),
                            checklist = existingChecklist,
                            showChecklist = existingChecklist.isNotEmpty(),
                            places = places,
                            placeId = reminder.placeId,
                            placeTrigger = PlaceTrigger.parse(reminder.placeTrigger)
                                ?: PlaceTrigger.ARRIVE,
                            placeRadiusMeters = places.find { it.id == reminder.placeId }
                                ?.radiusMeters ?: DEFAULT_PLACE_RADIUS_METERS,
                            placeWindowStartMinute = reminder.placeWindowStartMinute,
                            placeWindowEndMinute = reminder.placeWindowEndMinute,
                            placeWindowDays = reminder.placeWindowDays,
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

    fun openWhere() {
        _uiState.value = _uiState.value.copy(mode = CaptureMode.WHERE)
    }

    /** Picks a saved place, adopting its radius so the slider starts truthful. */
    fun selectPlace(placeId: Long?) {
        val state = _uiState.value
        val radius = state.places.find { it.id == placeId }?.radiusMeters
            ?: DEFAULT_PLACE_RADIUS_METERS
        _uiState.value = state.copy(placeId = placeId, placeRadiusMeters = radius)
    }

    fun setPlaceTrigger(trigger: PlaceTrigger) {
        _uiState.value = _uiState.value.copy(placeTrigger = trigger)
    }

    /**
     * The radius belongs to the *place*, not to this reminder, so changing it
     * here changes it for every reminder on that place. Saved straight away
     * rather than on Send: the slider is the only place it can be edited, and a
     * radius that silently reverted when the sheet was dismissed would be worse
     * than one that shares.
     */
    fun setPlaceRadius(meters: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(placeRadiusMeters = meters)
        val place = state.place ?: return
        if (place.radiusMeters == meters) return
        appScope.launch {
            placeRepository.savePlace(place.copy(radiusMeters = meters))
            refreshPlaces()
        }
    }

    fun setPlaceWindow(startMinute: Int?, endMinute: Int?) {
        _uiState.value = _uiState.value.copy(
            placeWindowStartMinute = startMinute,
            placeWindowEndMinute = endMinute
        )
    }

    fun setPlaceWindowDays(days: String?) {
        _uiState.value = _uiState.value.copy(placeWindowDays = days?.takeIf { it.isNotBlank() })
    }

    fun clearPlace() {
        _uiState.value = _uiState.value.copy(
            placeId = null,
            placeWindowStartMinute = null,
            placeWindowEndMinute = null,
            placeWindowDays = null
        )
    }

    /** Saves a place the user just searched for, and selects it. */
    fun createPlace(name: String, latitude: Double, longitude: Double, address: String?) {
        appScope.launch {
            val id = placeRepository.savePlace(
                PlaceEntity(
                    name = name,
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = _uiState.value.placeRadiusMeters,
                    address = address
                )
            )
            refreshPlaces()
            selectPlace(id)
        }
    }

    private suspend fun refreshPlaces() {
        _uiState.value = _uiState.value.copy(places = placeRepository.observePlaces().first())
    }

    fun collapseToTyping() {
        _uiState.value = _uiState.value.copy(mode = CaptureMode.TYPING)
    }

    fun closeRepeat() {
        _uiState.value = _uiState.value.copy(mode = modeBeforeRepeat)
    }

    // The due date the All day switch made up on the user's behalf, so that
    // switching it back off can take that date away again. Cleared as soon as
    // the user names a date or time themselves, since it's then theirs, not ours.
    private var allDayInventedDueAt: Long? = null

    fun setAllDay(allDay: Boolean) {
        dueOverridden = true
        val state = _uiState.value

        // "All day" with no date at all is a switch that does nothing visible.
        // Turning it on therefore means "all day today" unless a date is set.
        if (allDay && state.dueAt == null) {
            val invented = LocalDate.now(zone).atTime(DEFAULT_TIME).atZone(zone).toInstant().toEpochMilli()
            allDayInventedDueAt = invented
            _uiState.value = state.copy(isAllDay = true, dueAt = invented, parsedFromText = false)
            return
        }

        // Switching it back off has to take that invented date with it, or a
        // stray tap on the switch leaves behind a due date nobody asked for —
        // one the When sheet gives no way to remove.
        val dueAt = if (!allDay && state.dueAt == allDayInventedDueAt) null else state.dueAt
        if (dueAt == null) allDayInventedDueAt = null
        _uiState.value = state.copy(isAllDay = allDay, dueAt = dueAt, parsedFromText = false)
    }

    /** Sets both halves at once — what the quick-pick chips do. */
    fun setDueAt(epochMillis: Long?) {
        dueOverridden = true
        allDayInventedDueAt = null
        _uiState.value = _uiState.value.copy(
            dueAt = epochMillis,
            // Every chip names a specific time, so an all-day flag left over
            // from the parser would throw that time away: all-day reminders
            // alert at 09:00, which for "Tonight 7 pm" is a moment already
            // gone, and a trigger in the past is never scheduled at all.
            isAllDay = false,
            parsedFromText = false
        )
    }

    /**
     * Changes the day, keeping whatever time is already set.
     *
     * Date and time are edited separately in the When sheet but stored as one
     * instant, so each setter has to reconstruct the other half rather than
     * overwrite it — picking a date must not silently reset 7:00 pm to midnight.
     */
    fun setDate(date: LocalDate) {
        dueOverridden = true
        allDayInventedDueAt = null
        val state = _uiState.value
        val time = state.dueAt
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            ?: defaultTimeOn(date, zone)
        _uiState.value = state.copy(
            dueAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
            parsedFromText = false
        )
    }

    /** Changes the time, keeping the day. See [setDate]. */
    fun setTime(time: LocalTime) {
        dueOverridden = true
        allDayInventedDueAt = null
        val state = _uiState.value
        val existingDate = state.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        // With no date chosen yet, a bare time means the next time it's that
        // time — the same thing a clock alarm does. Anchoring it to today would
        // hand back an instant already in the past, which never fires.
        val date = existingDate ?: run {
            val now = ZonedDateTime.now(zone)
            if (now.toLocalTime() < time) now.toLocalDate() else now.toLocalDate().plusDays(1)
        }
        _uiState.value = state.copy(
            dueAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
            // A specific time and "all day" contradict each other; the more
            // specific of the two wins.
            isAllDay = false,
            parsedFromText = false
        )
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

    // --- Checklist -------------------------------------------------------

    /** Toggling the Checklist action opens the section, seeded with one empty row to type into. */
    fun toggleChecklist() {
        val state = _uiState.value
        _uiState.value = if (state.showChecklist && state.checklist.all { it.text.isBlank() }) {
            state.copy(showChecklist = false, checklist = emptyList())
        } else {
            state.copy(
                showChecklist = true,
                checklist = state.checklist.ifEmpty { listOf(ChecklistItemDraft("")) }
            )
        }
    }

    fun updateChecklistItem(index: Int, text: String) {
        val items = _uiState.value.checklist.toMutableList()
        if (index !in items.indices) return
        items[index] = items[index].copy(text = text)
        _uiState.value = _uiState.value.copy(checklist = items)
    }

    fun toggleChecklistItem(index: Int) {
        val items = _uiState.value.checklist.toMutableList()
        if (index !in items.indices) return
        items[index] = items[index].copy(isCompleted = !items[index].isCompleted)
        _uiState.value = _uiState.value.copy(checklist = items)
    }

    fun removeChecklistItem(index: Int) {
        val items = _uiState.value.checklist.toMutableList()
        if (index !in items.indices) return
        items.removeAt(index)
        _uiState.value = _uiState.value.copy(checklist = items)
    }

    fun addChecklistItem() {
        // Don't stack up blank rows if the user taps "Add an item" repeatedly.
        val items = _uiState.value.checklist
        if (items.lastOrNull()?.text?.isBlank() == true) return
        _uiState.value = _uiState.value.copy(checklist = items + ChecklistItemDraft(""))
    }

    // --- List ------------------------------------------------------------

    fun openListPicker() {
        _uiState.value = _uiState.value.copy(mode = CaptureMode.LIST)
    }

    fun selectList(listId: Long) {
        _uiState.value = _uiState.value.copy(listId = listId, mode = CaptureMode.TYPING)
    }

    /** Inline "New list" from the picker, so capture isn't interrupted by a trip to Lists. */
    fun createListAndSelect(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        scope.launch {
            val existing = _uiState.value.lists
            val newId = listRepository.createList(
                name = trimmed,
                colorArgb = NEW_LIST_COLORS[existing.size % NEW_LIST_COLORS.size],
                position = existing.size
            )
            _uiState.value = _uiState.value.copy(
                lists = listRepository.observeLists().first(),
                listId = newId,
                mode = CaptureMode.TYPING
            )
        }
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

        // A trigger without a place is nothing to register, and the geofence
        // columns are only meaningful together.
        val placeToSave = state.placeId?.let { id ->
            PlaceTriggerDraft(
                placeId = id,
                trigger = state.placeTrigger.name,
                windowStartMinute = state.placeWindowStartMinute,
                windowEndMinute = state.placeWindowEndMinute,
                windowDays = state.placeWindowDays
            )
        }

        val checklistItems = state.checklist
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() }

        scope.launch {
            when (target) {
                is CaptureTarget.New -> {
                    val newId = reminderRepository.createReminder(
                        listId = listId,
                        title = titleToSave,
                        note = state.note,
                        dueAt = dueToSave,
                        isAllDay = state.isAllDay,
                        repeatRule = ruleToSave,
                        place = placeToSave
                    )
                    if (checklistItems.isNotEmpty()) {
                        checklistRepository.replaceItems(newId, checklistItems)
                    }
                }
                is CaptureTarget.Edit -> {
                    reminderRepository.updateReminderFields(
                        id = target.reminderId,
                        title = titleToSave,
                        note = state.note,
                        listId = listId,
                        dueAt = dueToSave,
                        isAllDay = state.isAllDay,
                        repeatRule = ruleToSave,
                        place = placeToSave
                    )
                    // Always called on edit, including with an empty list, so
                    // deleting every item actually removes the checklist.
                    checklistRepository.replaceItems(target.reminderId, checklistItems)
                }
            }
            _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
        }
    }

    private companion object {
        // Matches the palette the Lists screen offers, so an inline-created
        // list doesn't look foreign next to one made the normal way.
        val NEW_LIST_COLORS = listOf(
            0xFFA03E28.toInt(), 0xFF006A60.toInt(), 0xFF7D5260.toInt(),
            0xFF4A6363.toInt(), 0xFF6750A4.toInt()
        )
    }
}
