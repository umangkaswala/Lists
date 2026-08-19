package com.stackpointer.lists.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Design S16's three-segment Light / Dark / System control. */
enum class ThemeChoice { LIGHT, DARK, SYSTEM }

/**
 * The three quick-time chips under the When editor's date and time fields.
 *
 * Stored as *rules*, not instants: "Tonight 7 pm" has to mean 7 pm whenever the
 * sheet is opened, so what's saved is the shape of each chip and the label is
 * regenerated every time. See `capture/QuickTimePresets.kt`.
 */
data class QuickTimeSettings(
    /** The relative chip — "In 1 hour". */
    val relativeMinutes: Int = 60,
    /** The evening chip — "Tonight 7 pm", minutes past midnight. */
    val eveningMinuteOfDay: Int = 19 * 60,
    /** The next-morning chip — "Tomorrow 9 am", minutes past midnight. */
    val morningMinuteOfDay: Int = 9 * 60
)

/**
 * Everything design S16 lets the user change, in one immutable snapshot.
 *
 * One object rather than a flow per row: the theme, the palette and the
 * quick-time chips are all read together on the way into a screen, and a
 * half-dozen separate flows would each produce their own frame.
 *
 * The defaults here are the values the design's own mock-up shows, so a fresh
 * install matches the drawing.
 */
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColour: Boolean = false,
    /** When an all-day reminder alerts, in minutes past midnight. */
    val allDayAlertMinuteOfDay: Int = 9 * 60,
    /** "Nudge me again if ignored" — re-post an unanswered alert. */
    val nudgeWhenIgnored: Boolean = true,
    /** "Read dates and places from my text" — the on-device capture parser. */
    val parseTypedText: Boolean = true,
    val quickTimes: QuickTimeSettings = QuickTimeSettings(),
    /** "Keep deleted items" — recycle-bin retention in days. */
    val binRetentionDays: Int = 30
)

/** [AppSettings.allDayAlertMinuteOfDay] as a clock time. */
val AppSettings.allDayAlertTime: LocalTime
    get() = LocalTime.ofSecondOfDay(allDayAlertMinuteOfDay * 60L)

/**
 * Reads and writes the Settings screen's preferences.
 *
 * DataStore, like the other two stores in this package, and for the same
 * reason: these are preferences about the app rather than the user's data, and
 * they must not ride along in an export of the reminders.
 *
 * Every read falls back to [AppSettings]'s own defaults, so a missing key —
 * a fresh install, or a setting added in a later version — behaves exactly
 * like a fresh install rather than crashing or reading zero.
 */
class SettingsStore(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme")
    private val dynamicColourKey = booleanPreferencesKey("dynamic_colour")
    private val allDayAlertKey = intPreferencesKey("all_day_alert_minute")
    private val nudgeKey = booleanPreferencesKey("nudge_when_ignored")
    private val parseKey = booleanPreferencesKey("parse_typed_text")
    private val quickRelativeKey = intPreferencesKey("quick_relative_minutes")
    private val quickEveningKey = intPreferencesKey("quick_evening_minute")
    private val quickMorningKey = intPreferencesKey("quick_morning_minute")
    private val binRetentionKey = intPreferencesKey("bin_retention_days")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            // A stored name that no longer matches an enum constant (a
            // downgrade, or a hand-edited file) falls back rather than throwing
            // inside a flow nothing is placed to catch.
            theme = prefs[themeKey]?.let { name ->
                ThemeChoice.entries.firstOrNull { it.name == name }
            } ?: defaults.theme,
            dynamicColour = prefs[dynamicColourKey] ?: defaults.dynamicColour,
            allDayAlertMinuteOfDay = prefs[allDayAlertKey] ?: defaults.allDayAlertMinuteOfDay,
            nudgeWhenIgnored = prefs[nudgeKey] ?: defaults.nudgeWhenIgnored,
            parseTypedText = prefs[parseKey] ?: defaults.parseTypedText,
            quickTimes = QuickTimeSettings(
                relativeMinutes = prefs[quickRelativeKey] ?: defaults.quickTimes.relativeMinutes,
                eveningMinuteOfDay = prefs[quickEveningKey] ?: defaults.quickTimes.eveningMinuteOfDay,
                morningMinuteOfDay = prefs[quickMorningKey] ?: defaults.quickTimes.morningMinuteOfDay
            ),
            binRetentionDays = prefs[binRetentionKey] ?: defaults.binRetentionDays
        )
    }

    suspend fun setTheme(choice: ThemeChoice) = edit { it[themeKey] = choice.name }

    suspend fun setDynamicColour(enabled: Boolean) = edit { it[dynamicColourKey] = enabled }

    suspend fun setAllDayAlertMinuteOfDay(minuteOfDay: Int) =
        edit { it[allDayAlertKey] = minuteOfDay.coerceIn(0, 24 * 60 - 1) }

    suspend fun setNudgeWhenIgnored(enabled: Boolean) = edit { it[nudgeKey] = enabled }

    suspend fun setParseTypedText(enabled: Boolean) = edit { it[parseKey] = enabled }

    suspend fun setQuickTimes(quickTimes: QuickTimeSettings) = edit { prefs ->
        prefs[quickRelativeKey] = quickTimes.relativeMinutes.coerceIn(1, 24 * 60)
        prefs[quickEveningKey] = quickTimes.eveningMinuteOfDay.coerceIn(0, 24 * 60 - 1)
        prefs[quickMorningKey] = quickTimes.morningMinuteOfDay.coerceIn(0, 24 * 60 - 1)
    }

    suspend fun setBinRetentionDays(days: Int) = edit { it[binRetentionKey] = days }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
