package com.stackpointer.lists.recurrence

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

/**
 * Expands an [RRule] into concrete occurrence date-times.
 *
 * Arithmetic is done on [LocalDate]/[LocalDateTime] (local wall-clock) and only
 * converted to a zoned instant at the very end via [LocalDate.atTime]/[atZone] —
 * never by adding a fixed [java.time.Duration] of days — so a reminder's
 * time-of-day stays stable across DST transitions instead of drifting by an
 * hour.
 */
object RRuleExpander {

    /**
     * Safety cap on how many candidate dates [dateSequence] will *examine*
     * internally before giving up — not how many it yields. This matters
     * because for some rules almost every candidate is rejected by the
     * "skip, don't clamp" logic (e.g. `FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30`
     * rejects every single candidate, forever, since 30 Feb never exists).
     * Bounding only the yield count would never terminate for such a rule.
     * Comfortably covers ~200 years even for DAILY (365 * 200 ≈ 73,000)
     * while guaranteeing termination for a non-terminating series (no
     * UNTIL/COUNT, or one that can never produce a valid date at all).
     */
    private const val MAX_CANDIDATES = 200_000

    /**
     * The first [limit] occurrences at or after [start], in ascending order.
     * [start] itself is occurrence #1 if it satisfies the rule. Respects
     * UNTIL and COUNT.
     */
    fun occurrencesFrom(rule: RRule, start: ZonedDateTime, limit: Int): List<ZonedDateTime> {
        if (limit <= 0) return emptyList()
        val zone = start.zone
        val time = start.toLocalTime()
        val result = mutableListOf<ZonedDateTime>()
        var index = 0
        for (date in dateSequence(rule, start.toLocalDate())) {
            index++
            if (rule.until != null && date.isAfter(rule.until)) break
            if (rule.count != null && index > rule.count) break
            result.add(date.atTime(time).atZone(zone))
            if (result.size >= limit) break
        }
        return result
    }

    /**
     * The first occurrence strictly after [after]. UNTIL/COUNT are evaluated
     * relative to [start] (the reminder's original due time), not to
     * [after]. Returns null when the series has ended (or the internal
     * search cap is hit without finding one — see [MAX_CANDIDATES]).
     */
    fun nextAfter(rule: RRule, start: ZonedDateTime, after: ZonedDateTime): ZonedDateTime? {
        val zone = start.zone
        val time = start.toLocalTime()
        var index = 0
        for (date in dateSequence(rule, start.toLocalDate())) {
            index++
            if (rule.until != null && date.isAfter(rule.until)) return null
            if (rule.count != null && index > rule.count) return null
            val candidate = date.atTime(time).atZone(zone)
            // ZonedDateTime.isAfter compares the instant only, which is what
            // we want here regardless of what zone `after` happens to carry.
            if (candidate.isAfter(after)) return candidate
        }
        return null
    }

    /**
     * The raw, ascending sequence of candidate occurrence dates for [rule]
     * starting from [startDate] (inclusive), ignoring UNTIL/COUNT entirely
     * — those are applied by the callers above. Unbounded in principle for
     * a rule with neither UNTIL nor COUNT, but internally self-terminating
     * after [MAX_CANDIDATES] *examined* candidates (yielded or not) so a
     * rule that can never produce a valid date — e.g. BYMONTHDAY=30 pinned
     * to February — can't spin forever rejecting candidates one by one
     * without ever yielding.
     */
    private fun dateSequence(rule: RRule, startDate: LocalDate): Sequence<LocalDate> = sequence {
        var examined = 0
        // Returns true once the cap is hit, having already counted this step.
        fun capReached(): Boolean {
            examined++
            return examined > MAX_CANDIDATES
        }

        when (rule.freq) {
            Freq.DAILY -> {
                var date = startDate
                while (!capReached()) {
                    yield(date)
                    date = date.plusDays(rule.interval.toLong())
                }
            }

            Freq.WEEKLY -> {
                if (rule.byDay.isEmpty()) {
                    var date = startDate
                    while (!capReached()) {
                        yield(date)
                        date = date.plusWeeks(rule.interval.toLong())
                    }
                } else {
                    // WKST=MO: weeks are anchored to the Monday of the week
                    // containing startDate, then advance by `interval` weeks.
                    val startWeekMonday = startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())
                    val sortedDays = rule.byDay.sortedBy { it.value } // MONDAY=1 .. SUNDAY=7
                    var weekMonday = startWeekMonday
                    outer@ while (true) {
                        for (day in sortedDays) {
                            if (capReached()) break@outer
                            val candidate = weekMonday.plusDays((day.value - 1).toLong())
                            if (!candidate.isBefore(startDate)) {
                                yield(candidate)
                            }
                        }
                        weekMonday = weekMonday.plusWeeks(rule.interval.toLong())
                    }
                }
            }

            Freq.MONTHLY -> {
                val day = rule.byMonthDay ?: startDate.dayOfMonth
                var monthStart = LocalDate.of(startDate.year, startDate.month, 1)
                while (!capReached()) {
                    val yearMonth = YearMonth.from(monthStart)
                    if (day <= yearMonth.lengthOfMonth()) {
                        val candidate = monthStart.withDayOfMonth(day)
                        if (!candidate.isBefore(startDate)) {
                            yield(candidate)
                        }
                    }
                    // Per RFC 5545, a month with no such day-of-month is
                    // skipped entirely rather than clamped (e.g. no Feb 31).
                    // `capReached()` still advances every iteration, so a
                    // BYMONTHDAY that's never valid for this rule's months
                    // (e.g. 31 pinned to February) still terminates.
                    monthStart = monthStart.plusMonths(rule.interval.toLong())
                }
            }

            Freq.YEARLY -> {
                val month = rule.byMonth ?: startDate.monthValue
                val day = rule.byMonthDay ?: startDate.dayOfMonth
                var year = startDate.year
                while (!capReached()) {
                    val yearMonth = YearMonth.of(year, month)
                    if (day <= yearMonth.lengthOfMonth()) {
                        val candidate = LocalDate.of(year, month, day)
                        if (!candidate.isBefore(startDate)) {
                            yield(candidate)
                        }
                    }
                    // Same skip-don't-clamp rule, e.g. 29 Feb only on leap
                    // years, or 30 Feb which never exists at all.
                    year += rule.interval
                }
            }
        }
    }
}
