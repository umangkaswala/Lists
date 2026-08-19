package com.stackpointer.lists.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stackpointer.lists.data.prefs.QuickTimeSettings
import com.stackpointer.lists.di.currentAppContainer
import com.stackpointer.lists.parser.formatDueChip
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class QuickTimePreset(val label: String, val epochMillis: Long)

/**
 * The three chips under the When editor's date and time fields.
 *
 * The design (S07) shows three outlined chips whose labels state the actual
 * time they set — "In 1 hour" / "Tonight 7 pm" / "Tomorrow 9 am" — rather than
 * vague words like "Later today". A label that names the time is the only way
 * the chips are honest about what they do, which is also why the labels are
 * *derived* from [settings] rather than stored beside them: a saved label would
 * go stale the moment the time behind it changed.
 *
 * Pure, and taking [now] as a parameter, so the rollover rules below can be
 * unit-tested without a device or a clock — the same treatment the RRULE engine
 * and the parser get.
 */
fun quickTimePresets(settings: QuickTimeSettings, now: ZonedDateTime): List<QuickTimePreset> {
    val zone = now.zone
    val relative = now.plusMinutes(settings.relativeMinutes.toLong()).withSecond(0).withNano(0)

    // "Tonight" is only tonight while tonight is still ahead. Past the evening
    // time the chip would set a moment already gone, and an alarm in the past
    // never fires at all — so it rolls to tomorrow and the label says so.
    val evening = LocalTime.ofSecondOfDay(settings.eveningMinuteOfDay * 60L)
    val eveningIsToday = now.toLocalTime() < evening
    val eveningDate = if (eveningIsToday) now.toLocalDate() else now.toLocalDate().plusDays(1)
    val eveningWhen = if (eveningIsToday) "Tonight" else "Tomorrow"

    val morning = LocalTime.ofSecondOfDay(settings.morningMinuteOfDay * 60L)
    val morningDate = now.toLocalDate().plusDays(1)

    return listOf(
        QuickTimePreset(
            label = "In ${formatDuration(settings.relativeMinutes)}",
            epochMillis = relative.toInstant().toEpochMilli()
        ),
        QuickTimePreset(
            label = "$eveningWhen ${formatClock(evening)}",
            epochMillis = eveningDate.atTime(evening).atZone(zone).toInstant().toEpochMilli()
        ),
        QuickTimePreset(
            label = "Tomorrow ${formatClock(morning)}",
            epochMillis = morningDate.atTime(morning).atZone(zone).toInstant().toEpochMilli()
        )
    )
}

/**
 * Empty until the stored settings have actually been read.
 *
 * Not seeded with [QuickTimeSettings]'s defaults on purpose. These chips are
 * *tappable*: someone who set "Soon" to 15 minutes would otherwise be offered a
 * chip labelled "In 1 hour" that really did set an hour, for as long as the
 * first read took. No chip is better than a wrong one you can press.
 *
 * The result is deliberately not `remember`ed: these are absolute instants
 * computed from "now", and a sheet left open across the hour would otherwise
 * keep offering an "In 1 hour" that has already been and gone. Three date
 * calculations per recomposition is nothing.
 */
@Composable
fun quickTimePresets(): List<QuickTimePreset> {
    val container = currentAppContainer()
    // remember-ed so the flow isn't rebuilt (and re-collected from scratch) on
    // every recomposition — which, given this function recomposes freely, would
    // mean a DataStore read per frame.
    val quickTimesFlow = remember(container) {
        container.settingsStore.settings.map { it.quickTimes }.distinctUntilChanged()
    }
    val quickTimes by quickTimesFlow.collectAsStateWithLifecycle(initialValue = null)
    val settings = quickTimes ?: return emptyList()
    return quickTimePresets(settings, ZonedDateTime.now(ZoneId.systemDefault()))
}

/** "1 hour", "30 minutes", "2 hours" — for the relative chip's label. */
fun formatDuration(minutes: Int): String = when {
    minutes % 60 == 0 && minutes / 60 == 1 -> "1 hour"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    minutes == 1 -> "1 minute"
    else -> "$minutes minutes"
}

/**
 * "7 pm" for a whole hour, "7:30 pm" otherwise — the design's own wording.
 *
 * Always 12-hour: these are chip *labels* that have to read as English next to
 * the words "Tonight" and "Tomorrow", unlike the When editor's time field,
 * which follows the phone's 12/24-hour setting because it shows a value the
 * user is about to edit.
 */
fun formatClock(time: LocalTime): String {
    val pattern = if (time.minute == 0) "h a" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)).lowercase(Locale.ENGLISH)
}

/**
 * Chip text for a due date the app already holds as epoch millis.
 *
 * Defers to [formatDueChip] — the design's own "Tue, 19 Aug, 7:00 pm" wording —
 * for everything except today and tomorrow, where a relative word is clearer
 * than a date. Kept separate from [formatDueChip] rather than folded into it
 * because that function is pure (no dependency on "now") and its tests rely on
 * staying that way.
 */
fun formatDueLabel(epochMillis: Long, isAllDay: Boolean): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.now(zone)
    val relativeDate = when (dateTime.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> return formatDueChip(dateTime, isAllDay)
    }
    if (isAllDay) return relativeDate
    val time = dateTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase(Locale.ENGLISH)
    return "$relativeDate, $time"
}
