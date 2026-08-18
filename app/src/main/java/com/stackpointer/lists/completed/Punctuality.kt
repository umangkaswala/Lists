package com.stackpointer.lists.completed

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * How close to its due time a completion was: "on time", "12 min late", or null
 * when the reminder had no due time to be measured against.
 *
 * Shared by the Completed list and Detail's history card so the same completion
 * can never be described two different ways on two screens.
 *
 * An all-day reminder is judged on the date alone — a 9 pm tick on the right
 * day is not "12 hours late", it is simply done.
 */
fun punctualityLabel(
    dueAt: Long?,
    completedAt: Long,
    wasAllDay: Boolean,
    zone: ZoneId
): String? {
    if (dueAt == null) return null
    val due = Instant.ofEpochMilli(dueAt).atZone(zone)
    val done = Instant.ofEpochMilli(completedAt).atZone(zone)
    if (wasAllDay) {
        val daysLate = Duration.between(
            due.toLocalDate().atStartOfDay(zone),
            done.toLocalDate().atStartOfDay(zone)
        ).toDays()
        return if (daysLate <= 0) ON_TIME else plural(daysLate.toInt(), "day") + " late"
    }
    val late = Duration.between(due, done)
    return when {
        late.toMinutes() < 1 -> ON_TIME
        late.toMinutes() < 60 -> "${late.toMinutes()} min late"
        late.toHours() < 24 -> plural(late.toHours().toInt(), "hour") + " late"
        else -> plural(late.toDays().toInt(), "day") + " late"
    }
}

const val ON_TIME = "on time"
