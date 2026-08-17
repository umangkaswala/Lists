package com.stackpointer.lists.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The subset of RFC 5545 recurrence rules this app supports.
 *
 * Stored on [com.stackpointer.lists.data.entity.ReminderEntity.repeatRule] as the
 * bare parameter string (no "RRULE:" prefix), e.g. `FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,FR`.
 *
 * Deliberately narrow: no BYSETPOS, no BYWEEKNO, no BYYEARDAY, no positional
 * BYDAY ("2nd Tuesday"). Those aren't reachable from the Repeat screen's UI, so
 * supporting them would be untested code. [parse] returns null rather than
 * silently mangling a rule it doesn't understand.
 */
enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

data class RRule(
    val freq: Freq,
    val interval: Int = 1,
    /** Only meaningful for WEEKLY. Empty means "same weekday as the start date". */
    val byDay: Set<DayOfWeek> = emptySet(),
    /** Only meaningful for MONTHLY/YEARLY. Null means "same day-of-month as the start date". */
    val byMonthDay: Int? = null,
    /** Only meaningful for YEARLY. Null means "same month as the start date". 1..12. */
    val byMonth: Int? = null,
    /** Inclusive last date the rule may produce. Mutually exclusive with [count]. */
    val until: LocalDate? = null,
    /** Total number of occurrences including the first. Mutually exclusive with [until]. */
    val count: Int? = null
) {
    init {
        require(interval >= 1) { "interval must be >= 1" }
        require(count == null || until == null) { "count and until are mutually exclusive" }
        require(count == null || count >= 1) { "count must be >= 1" }
        require(byMonthDay == null || byMonthDay in 1..31) { "byMonthDay must be 1..31" }
        require(byMonth == null || byMonth in 1..12) { "byMonth must be 1..12" }
    }

    /** Serializes to the RFC 5545 parameter string, e.g. `FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,FR`. */
    fun toRRuleString(): String {
        val parts = mutableListOf("FREQ=${freq.name}")
        if (interval != 1) parts += "INTERVAL=$interval"
        if (byDay.isNotEmpty()) {
            // RFC order is significant to no one, but a stable order keeps
            // serialize -> parse -> serialize round-trips byte-identical.
            parts += "BYDAY=" + DayOfWeek.entries
                .filter { it in byDay }
                .joinToString(",") { ICAL_DAYS.getValue(it) }
        }
        byMonthDay?.let { parts += "BYMONTHDAY=$it" }
        byMonth?.let { parts += "BYMONTH=$it" }
        until?.let { parts += "UNTIL=" + it.format(UNTIL_FORMAT) }
        count?.let { parts += "COUNT=$it" }
        return parts.joinToString(";")
    }

    companion object {
        private val UNTIL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

        val ICAL_DAYS: Map<DayOfWeek, String> = mapOf(
            DayOfWeek.MONDAY to "MO",
            DayOfWeek.TUESDAY to "TU",
            DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR",
            DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU"
        )

        private val DAYS_BY_ICAL: Map<String, DayOfWeek> = ICAL_DAYS.entries.associate { it.value to it.key }

        /**
         * Parses a stored rule string. Returns null for anything malformed or
         * outside the supported subset — callers treat null as "doesn't repeat"
         * rather than crashing on data written by a future version of the app.
         */
        fun parse(rule: String?): RRule? {
            if (rule.isNullOrBlank()) return null
            val body = rule.removePrefix("RRULE:").trim()
            val pairs = mutableMapOf<String, String>()
            for (segment in body.split(';')) {
                if (segment.isBlank()) continue
                val idx = segment.indexOf('=')
                if (idx <= 0) return null
                pairs[segment.substring(0, idx).uppercase()] = segment.substring(idx + 1).uppercase()
            }

            val freq = pairs["FREQ"]?.let { name -> Freq.entries.find { it.name == name } } ?: return null
            // Reject a malformed INTERVAL rather than silently defaulting to 1,
            // which would turn "every 2 weeks" into "every week" on bad data.
            val interval = pairs["INTERVAL"]?.let { it.toIntOrNull() ?: return null } ?: 1
            if (interval < 1) return null

            val byDay = pairs["BYDAY"]
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?.map { DAYS_BY_ICAL[it.trim()] ?: return null }
                ?.toSet()
                ?: emptySet()

            val byMonthDay = pairs["BYMONTHDAY"]?.let { it.toIntOrNull() ?: return null }
            if (byMonthDay != null && byMonthDay !in 1..31) return null
            val byMonth = pairs["BYMONTH"]?.let { it.toIntOrNull() ?: return null }
            if (byMonth != null && byMonth !in 1..12) return null

            val until = pairs["UNTIL"]?.let { raw ->
                // Accept both date-only (20261231) and date-time (20261231T235959Z) forms.
                val datePart = raw.substringBefore('T')
                runCatching { LocalDate.parse(datePart, UNTIL_FORMAT) }.getOrNull() ?: return null
            }
            val count = pairs["COUNT"]?.let { it.toIntOrNull() ?: return null }
            if (count != null && count < 1) return null
            if (count != null && until != null) return null

            return RRule(freq, interval, byDay, byMonthDay, byMonth, until, count)
        }
    }
}
