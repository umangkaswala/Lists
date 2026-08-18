package com.stackpointer.lists.places

import com.stackpointer.lists.data.entity.ReminderEntity
import java.time.DayOfWeek
import java.time.ZonedDateTime

/** Which way across the boundary should alert. */
enum class PlaceTrigger {
    ARRIVE,
    LEAVE;

    companion object {
        fun parse(value: String?): PlaceTrigger? = when (value) {
            "ARRIVE" -> ARRIVE
            "LEAVE" -> LEAVE
            else -> null
        }
    }
}

/** Two-letter day codes, matching the RRULE BYDAY spelling used elsewhere. */
private val DAY_CODES = mapOf(
    DayOfWeek.MONDAY to "MO",
    DayOfWeek.TUESDAY to "TU",
    DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY to "TH",
    DayOfWeek.FRIDAY to "FR",
    DayOfWeek.SATURDAY to "SA",
    DayOfWeek.SUNDAY to "SU"
)

/**
 * Whether a place reminder is allowed to alert at [now].
 *
 * A geofence can't be given a schedule — Play Services registers it and that is
 * that — so the "only between" window from design S08 has to be enforced here,
 * at the moment the crossing is reported. Everything without a window passes.
 */
fun isWithinPlaceWindow(reminder: ReminderEntity, now: ZonedDateTime): Boolean {
    val days = reminder.placeWindowDays
    if (!days.isNullOrBlank()) {
        val today = DAY_CODES[now.dayOfWeek] ?: return true
        val allowed = days.split(",").map { it.trim().uppercase() }
        if (today !in allowed) return false
    }

    val start = reminder.placeWindowStartMinute
    val end = reminder.placeWindowEndMinute
    if (start == null || end == null) return true

    val minuteOfDay = now.hour * 60 + now.minute
    // A window whose end is *before* its start runs across midnight ("only
    // between 10 pm and 6 am"), so the test flips from AND to OR.
    //
    // Equal ends take the same branch deliberately: "from 9:00 to 9:00" then
    // evaluates to always-true, i.e. the whole day. Treating it as an inclusive
    // same-day range instead made it a one-minute window, silencing the
    // reminder for 1439 minutes out of every 1440 — while the editor cheerfully
    // said the window ran overnight.
    return if (start < end) {
        minuteOfDay in start..end
    } else {
        minuteOfDay >= start || minuteOfDay <= end
    }
}
