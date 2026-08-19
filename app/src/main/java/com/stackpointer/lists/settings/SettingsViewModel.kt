package com.stackpointer.lists.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stackpointer.lists.data.prefs.AppSettings
import com.stackpointer.lists.data.prefs.QuickTimeSettings
import com.stackpointer.lists.data.prefs.SettingsStore
import com.stackpointer.lists.data.prefs.ThemeChoice
import com.stackpointer.lists.data.repository.PlaceRepository
import com.stackpointer.lists.data.repository.ReminderAlarms
import com.stackpointer.lists.data.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val exporter: ReminderExporter,
    private val reminderRepository: ReminderRepository,
    private val alarms: ReminderAlarms,
    /**
     * For the one write here that must survive this screen being left: the
     * retention purge. `viewModelScope` dies with the screen, and the user
     * choosing "7 days" and immediately pressing back would cancel the delete
     * halfway — the scope-lifetime trap CLAUDE.md records from Phase 4.
     */
    private val appScope: CoroutineScope,
    placeRepository: PlaceRepository
) : ViewModel() {

    /**
     * Null until the first read. Every row on this screen states its current
     * value, so drawing the screen with defaults and correcting it a frame
     * later would show the user settings that aren't theirs.
     */
    val settings: StateFlow<AppSettings?> = settingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    /** For the "Saved places" row's support line — S16 shows the names inline. */
    val placeNames: StateFlow<List<String>?> = placeRepository.observePlaces()
        .map { places -> places.map { it.name } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun setTheme(choice: ThemeChoice) = launchEdit { settingsStore.setTheme(choice) }

    fun setDynamicColour(enabled: Boolean) = launchEdit { settingsStore.setDynamicColour(enabled) }

    /**
     * Every all-day reminder already has an alarm registered for the *old*
     * time, and nothing else would correct them until the app happened to be
     * resumed again. Without the re-sync the setting looks like it worked and
     * then quietly doesn't, which is the worst of both.
     */
    fun setAllDayAlertMinuteOfDay(minuteOfDay: Int) = launchEdit {
        settingsStore.setAllDayAlertMinuteOfDay(minuteOfDay)
        alarms.requestSync()
    }

    fun setNudgeWhenIgnored(enabled: Boolean) =
        launchEdit { settingsStore.setNudgeWhenIgnored(enabled) }

    fun setParseTypedText(enabled: Boolean) = launchEdit { settingsStore.setParseTypedText(enabled) }

    fun setQuickTimes(quickTimes: QuickTimeSettings) =
        launchEdit { settingsStore.setQuickTimes(quickTimes) }

    /**
     * Applied at once, not at the next app start. A window the user has just
     * shortened to seven days while thirty-day-old items sit in the bin is a
     * setting that isn't true yet, and the dialog says outright that this
     * happens.
     */
    fun setBinRetentionDays(days: Int) {
        appScope.launch {
            settingsStore.setBinRetentionDays(days)
            reminderRepository.purgeExpiredBin(days.toLong())
        }
    }

    /**
     * Builds the export file off the main thread and hands back a share intent,
     * or null if the file couldn't be written.
     */
    fun export(format: ExportFormat, onReady: (Intent?) -> Unit) {
        viewModelScope.launch { onReady(exporter.export(format)) }
    }

    private fun launchEdit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(
        private val settingsStore: SettingsStore,
        private val exporter: ReminderExporter,
        private val reminderRepository: ReminderRepository,
        private val alarms: ReminderAlarms,
        private val appScope: CoroutineScope,
        private val placeRepository: PlaceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                settingsStore,
                exporter,
                reminderRepository,
                alarms,
                appScope,
                placeRepository
            ) as T
        }
    }
}
