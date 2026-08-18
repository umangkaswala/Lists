package com.stackpointer.lists.capture

import androidx.compose.runtime.Composable
import com.stackpointer.lists.parser.formatDueChip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class QuickTimePreset(val label: String, val epochMillis: Long)

// The design (S07) shows three outlined chips whose labels state the actual
// time they set — "In 1 hour" / "Tonight 7 pm" / "Tomorrow 9 am" — rather than
// vague words like "Later today". A label that names the time is the only way
// the chips are honest about what they do.
//
// Becomes user-configurable in Settings (Phase 9), which is what will then feed
// this list instead.
//
// Deliberately not `remember`ed: these are absolute instants computed from
// "now", and a sheet left open across the hour would otherwise keep offering
// an "In 1 hour" that has already been and gone. Three date calculations per
// recomposition is nothing.
@Composable
fun quickTimePresets(): List<QuickTimePreset> {
    return run {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)

        val inAnHour = now.plusHours(1).withSecond(0).withNano(0)

        // "Tonight" is only tonight while tonight is still ahead. Past 7pm the
        // chip would set a time already gone, and an alarm in the past never
        // fires at all — so it rolls to tomorrow and the label says so.
        val evening = LocalTime.of(19, 0)
        val eveningDate = if (now.toLocalTime() < evening) {
            now.toLocalDate()
        } else {
            now.toLocalDate().plusDays(1)
        }
        val eveningLabel = if (eveningDate == now.toLocalDate()) "Tonight 7 pm" else "Tomorrow 7 pm"
        val tomorrowMorning = now.toLocalDate().plusDays(1).atTime(9, 0).atZone(zone)

        listOf(
            QuickTimePreset("In 1 hour", inAnHour.toInstant().toEpochMilli()),
            QuickTimePreset(eveningLabel, eveningDate.atTime(evening).atZone(zone).toInstant().toEpochMilli()),
            QuickTimePreset("Tomorrow 9 am", tomorrowMorning.toInstant().toEpochMilli())
        )
    }
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
