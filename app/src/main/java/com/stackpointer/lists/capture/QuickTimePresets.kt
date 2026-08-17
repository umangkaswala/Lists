package com.stackpointer.lists.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

fun formatDueLabel(epochMillis: Long, isAllDay: Boolean): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.now(zone)
    val datePart = when (dateTime.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> dateTime.format(DateTimeFormatter.ofPattern("MMM d"))
    }
    return if (isAllDay) datePart else "$datePart · ${dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))}"
}
