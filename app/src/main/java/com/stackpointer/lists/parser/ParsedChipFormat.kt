package com.stackpointer.lists.parser

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CHIP_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val CHIP_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/**
 * Chip label for a parsed due time, e.g. "Tue, 19 Aug, 7:00 pm" or
 * "Tue, 19 Aug" for an all-day reminder. Uses [Locale.ENGLISH] and a
 * lowercase am/pm marker to match the design's chip text exactly.
 */
fun formatDueChip(dueAt: ZonedDateTime, isAllDay: Boolean): String {
    val datePart = dueAt.format(CHIP_DATE_FORMAT)
    if (isAllDay) return datePart
    val timePart = dueAt.format(CHIP_TIME_FORMAT).lowercase(Locale.ENGLISH)
    return "$datePart, $timePart"
}
