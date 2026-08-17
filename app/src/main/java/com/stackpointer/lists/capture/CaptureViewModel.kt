package com.stackpointer.lists.capture

import com.stackpointer.lists.data.entity.ReminderListEntity
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.data.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class CaptureMode { TYPING, WHEN }

data class CaptureUiState(
    val mode: CaptureMode = CaptureMode.TYPING,
    val title: String = "",
    val note: String? = null,
    val listId: Long? = null,
    val lists: List<ReminderListEntity> = emptyList(),
    val dueAt: Long? = null,
    val isAllDay: Boolean = false,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val notFound: Boolean = false
) {
    val canSave: Boolean get() = title.isNotBlank() && !isSaving
    val listName: String get() = lists.find { it.id == listId }?.name ?: ""
}

// Deliberately not an androidx ViewModel: this is created directly with
// `remember(target, sheetKey)` in CaptureSheetContent, scoped to that
// composition. The ModalBottomSheet it lives in isn't a NavBackStackEntry
// destination, so there's no natural ViewModelStoreOwner to clear a real
// ViewModel from when the sheet closes — using one here leaked one retained
// instance per sheet open for the lifetime of the app process.
class CaptureViewModel(
    private val target: CaptureTarget,
    private val reminderRepository: ReminderRepository,
    private val listRepository: ListRepository,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            val lists = listRepository.observeLists().first()
            val defaultListId = lists.find { it.isDefault }?.id ?: lists.firstOrNull()?.id

            when (target) {
                is CaptureTarget.New -> {
                    _uiState.value = _uiState.value.copy(
                        title = target.prefillText,
                        lists = lists,
                        listId = defaultListId
                    )
                }
                is CaptureTarget.Edit -> {
                    val reminder = reminderRepository.observeById(target.reminderId).first()
                    if (reminder == null) {
                        _uiState.value = _uiState.value.copy(notFound = true)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            title = reminder.title,
                            note = reminder.note,
                            lists = lists,
                            listId = reminder.listId,
                            dueAt = reminder.dueAt,
                            isAllDay = reminder.isAllDay,
                            isEditing = true
                        )
                    }
                }
            }
        }
    }

    fun updateTitle(text: String) {
        _uiState.value = _uiState.value.copy(title = text)
    }

    fun openWhen() {
        _uiState.value = _uiState.value.copy(mode = CaptureMode.WHEN)
    }

    fun collapseToTyping() {
        _uiState.value = _uiState.value.copy(mode = CaptureMode.TYPING)
    }

    fun setAllDay(allDay: Boolean) {
        _uiState.value = _uiState.value.copy(isAllDay = allDay)
    }

    fun setDueAt(epochMillis: Long?) {
        _uiState.value = _uiState.value.copy(dueAt = epochMillis)
    }

    fun clearDue() {
        _uiState.value = _uiState.value.copy(dueAt = null, isAllDay = false)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val listId = state.listId ?: return
        _uiState.value = state.copy(isSaving = true)

        scope.launch {
            when (target) {
                is CaptureTarget.New -> {
                    reminderRepository.createReminder(
                        listId = listId,
                        title = state.title.trim(),
                        note = state.note,
                        dueAt = state.dueAt,
                        isAllDay = state.isAllDay
                    )
                }
                is CaptureTarget.Edit -> {
                    reminderRepository.updateReminderFields(
                        id = target.reminderId,
                        title = state.title.trim(),
                        note = state.note,
                        listId = listId,
                        dueAt = state.dueAt,
                        isAllDay = state.isAllDay
                    )
                }
            }
            _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
        }
    }
}
