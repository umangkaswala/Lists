package com.stackpointer.lists.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Human-readable summaries of an [RRule], used by the Repeat screen's
 * summary banner, the Detail screen's Repeat row, and the Capture sheet's
 * repeat chip.
 *
 * Weekday/month names are always formatted in English (`Locale.ENGLISH`) so
 * these strings — and the tests that assert on them — don't depend on the
 * device's locale.
 */

private val UNTIL_DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private val WEEKDAYS: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
)
private val WEEKEND_DAYS: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private fun dayFullName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

private fun monthFullName(month: Int): String = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH)

/** "Tuesday and Friday" / "Monday, Wednesday and Friday" — no Oxford comma. */
private fun joinWithAnd(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}

/** The days a WEEKLY rule applies to: its own BYDAY, or else [startDate]'s weekday. */
private fun effectiveWeekDays(rule: RRule, startDate: LocalDate): List<DayOfWeek> {
    val days = if (rule.byDay.isNotEmpty()) rule.byDay else setOf(startDate.dayOfWeek)
    return DayOfWeek.entries.filter { it in days }
}

/**
 * The frequency/interval/qualifier clause, e.g. "Every 2 weeks on Tuesday
 * and Friday" or "Monthly". [includeQualifiers] controls whether the
 * BYMONTHDAY/BYMONTH "on day 15" / "on 25 December" suffix is appended
 * (summary: yes; short chip label: no, for compactness).
 */
private fun baseClause(rule: RRule, startDate: LocalDate, includeQualifiers: Boolean): String {
    return when (rule.freq) {
        Freq.DAILY -> if (rule.interval == 1) "Daily" else "Every ${rule.interval} days"

        Freq.WEEKLY -> {
            val days = effectiveWeekDays(rule, startDate)
            // "Every weekday" beats spelling out five day names — the latter
            // wraps to two lines in the Capture sheet's repeat chip.
            val dayText = when (days.toSet()) {
                WEEKDAYS -> "weekday"
                WEEKEND_DAYS -> "weekend"
                else -> joinWithAnd(days.map(::dayFullName))
            }
            when {
                rule.interval == 1 -> "Every $dayText"
                includeQualifiers -> "Every ${rule.interval} weeks on $dayText"
                else -> "Every ${rule.interval} weeks"
            }
        }

        Freq.MONTHLY -> {
            val base = if (rule.interval == 1) "Monthly" else "Every ${rule.interval} months"
            if (includeQualifiers && rule.byMonthDay != null) "$base on day ${rule.byMonthDay}" else base
        }

        Freq.YEARLY -> {
            val base = if (rule.interval == 1) "Yearly" else "Every ${rule.interval} years"
            if (includeQualifiers && (rule.byMonth != null || rule.byMonthDay != null)) {
                val month = rule.byMonth ?: startDate.monthValue
                val day = rule.byMonthDay ?: startDate.dayOfMonth
                "$base on $day ${monthFullName(month)}"
            } else {
                base
            }
        }
    }
}

private fun endingClause(rule: RRule): String = when {
    rule.until != null -> ", until ${rule.until.format(UNTIL_DISPLAY_FORMAT)}"
    rule.count != null -> ", ${rule.count} times"
    else -> ""
}

/**
 * Human sentence for the Repeat screen's summary banner and the Detail
 * screen's Repeat row, e.g. "Every 2 weeks on Tuesday and Friday, until 31
 * Dec 2026".
 */
fun rruleSummary(rule: RRule, startDate: LocalDate): String =
    baseClause(rule, startDate, includeQualifiers = true) + endingClause(rule)

/**
 * Short form for the Capture sheet's repeat chip, e.g. "Every Tuesday",
 * "Every 2 weeks", "Daily", "Monthly" — no BYMONTHDAY/BYMONTH qualifier and
 * no UNTIL/COUNT ending clause.
 */
fun rruleShortLabel(rule: RRule, startDate: LocalDate): String =
    baseClause(rule, startDate, includeQualifiers = false)
