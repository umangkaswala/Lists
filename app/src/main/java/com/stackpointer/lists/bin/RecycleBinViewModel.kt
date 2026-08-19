package com.stackpointer.lists.bin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.data.prefs.SettingsStore
import com.stackpointer.lists.data.repository.DEFAULT_BIN_RETENTION_DAYS
import com.stackpointer.lists.data.repository.ReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class BinEntryUiModel(
    val id: Long,
    val title: String,
    val meta: String
)

data class RecycleBinUiState(
    val isLoading: Boolean = true,
    val entries: List<BinEntryUiModel> = emptyList(),
    /** Settings S16's "Keep deleted items", so the copy on this screen is true. */
    val retentionDays: Int = DEFAULT_BIN_RETENTION_DAYS
) {
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()
}

class RecycleBinViewModel(
    private val reminderRepository: ReminderRepository,
    settingsStore: SettingsStore
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val uiState: StateFlow<RecycleBinUiState> = combine(
        reminderRepository.observeDeleted(),
        settingsStore.settings.map { it.binRetentionDays }.distinctUntilChanged()
    ) { reminders, retentionDays ->
        RecycleBinUiState(
            isLoading = false,
            entries = reminders.map { toUiModel(it, retentionDays) },
            retentionDays = retentionDays
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecycleBinUiState())

    fun restore(ids: List<Long>) {
        viewModelScope.launch { reminderRepository.restoreFromBin(ids) }
    }

    fun deleteForever(ids: List<Long>) {
        viewModelScope.launch { reminderRepository.deleteForever(ids) }
    }

    private fun toUiModel(reminder: ReminderEntity, retentionDays: Int): BinEntryUiModel {
        // deletedAt is non-null for everything this query returns, but the
        // column is nullable, so fall back to "deleted now" rather than crash.
        val deletedAt = reminder.deletedAt ?: Instant.now().toEpochMilli()
        // Whole calendar days, not 24-hour blocks: something deleted at 11 pm
        // yesterday reads as "1 day ago" the next morning, which is what a
        // person would say.
        val deletedOn = Instant.ofEpochMilli(deletedAt).atZone(zone).toLocalDate()
        val daysGone = java.time.temporal.ChronoUnit.DAYS
            .between(deletedOn, LocalDate.now(zone))
            .coerceAtLeast(0)
        val daysLeft = (retentionDays - daysGone).coerceAtLeast(0)
        val deletedText = when (daysGone) {
            0L -> "Deleted today"
            1L -> "Deleted 1 day ago"
            else -> "Deleted $daysGone days ago"
        }
        val leftText = when (daysLeft) {
            0L -> "removed today"
            1L -> "1 day left"
            else -> "$daysLeft days left"
        }
        return BinEntryUiModel(
            id = reminder.id,
            title = reminder.title,
            meta = "$deletedText · $leftText"
        )
    }

    class Factory(
        private val reminderRepository: ReminderRepository,
        private val settingsStore: SettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecycleBinViewModel(reminderRepository, settingsStore) as T
        }
    }
}
