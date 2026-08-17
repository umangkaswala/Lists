package com.stackpointer.lists.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.stackpointer.lists.parser.formatDueChip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class QuickTimePreset(val label: String, val epochMillis: Long)

// Hardcoded for Phase 2. Becomes user-configurable in Settings (Phase 9),
// which is what will then feed this list instead.
@Composable
fun rememberQuickTimePresets(): List<QuickTimePreset> {
    return remember {
        val zone = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        val laterToday = now.plusHours(3)
        val tonight = if (now.toLocalTime().isBefore(LocalTime.of(19, 0))) {
            now.toLocalDate().atTime(19, 0).atZone(zone)
        } else {
            now.plusHours(3)
        }
        val tomorrowMorning = now.toLocalDate().plusDays(1).atTime(9, 0).atZone(zone)
        val nextWeek = now.toLocalDate().plusDays(7).atTime(9, 0).atZone(zone)

        listOf(
            QuickTimePreset("Later today", laterToday.toInstant().toEpochMilli()),
            QuickTimePreset("Tonight", tonight.toInstant().toEpochMilli()),
            QuickTimePreset("Tomorrow", tomorrowMorning.toInstant().toEpochMilli()),
            QuickTimePreset("Next week", nextWeek.toInstant().toEpochMilli())
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
