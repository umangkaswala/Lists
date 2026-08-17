package com.stackpointer.lists.parser

import com.stackpointer.lists.recurrence.Freq
import com.stackpointer.lists.recurrence.RRule
import com.stackpointer.lists.recurrence.RRuleExpander
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime

/** A half-open [start, end) index range into the original input text. */
data class ParsedSpan(val start: Int, val end: Int)

data class ParseResult(
    val dueAt: ZonedDateTime? = null,
    /** True when a date was found but no time-of-day — [dueAt]'s time defaults to 09:00 local. */
    val isAllDay: Boolean = false,
    val repeat: RRule? = null,
    /** Every stretch of the input text that was consumed while parsing date/time/repeat phrases. */
    val spans: List<ParsedSpan> = emptyList(),
    val cleanedTitle: String
)

/**
 * On-device natural-language parser for the Capture bottom sheet.
 *
 * Pure Kotlin / java.time — no Android APIs, no ML, no network. `now` is
 * always injected (never read from the system clock) so parsing is
 * deterministic and unit-testable. English only.
 *
 * The general strategy: every supported phrase has one or more regexes that
 * produce candidate matches tagged as REPEAT, DATE, TIME or DATETIME. All
 * candidates (across all three phrase families) are pooled and resolved
 * against each other by longest-match-wins, so e.g. "day after tomorrow"
 * beats a nested "tomorrow" and "every 2 weeks" beats a nested "every week".
 * Accepted spans are then grown leftward over glue words ("at", "on", "by",
 * "this", "next", "starting") so they don't leak into the cleaned title.
 */
object CaptureParser {

    fun parse(text: String, now: ZonedDateTime): ParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ParseResult(cleanedTitle = trimmed)
        return try {
            parseInternal(text, now)
        } catch (e: Exception) {
            ParseResult(cleanedTitle = trimmed)
        }
    }

    // -----------------------------------------------------------------
    // Word tables
    // -----------------------------------------------------------------

    private val WEEKDAY_TOKENS: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY
    )

    private val MONTH_TOKENS: Map<String, Int> = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    private const val WEEKDAY_ALT =
        "monday|mon|tuesday|tue|tues|wednesday|wed|thursday|thu|thur|thurs|friday|fri|saturday|sat|sunday|sun"
    private const val MONTH_ALT =
        "january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|" +
            "september|sept|sep|october|oct|november|nov|december|dec"

    private val GLUE_WORDS = setOf("at", "on", "by", "this", "next", "starting")

    // -----------------------------------------------------------------
    // Candidate model
    // -----------------------------------------------------------------

    private enum class Kind { REPEAT, DATE, TIME, DATETIME }

    private data class Candidate(
        val range: IntRange,
        val kind: Kind,
        val repeat: RRule? = null,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val exact: ZonedDateTime? = null
    )

    private fun parseInternal(text: String, now: ZonedDateTime): ParseResult {
        val candidates = mutableListOf<Candidate>()
        candidates += findRepeatCandidates(text)
        candidates += findDateCandidates(text, now)
        candidates += findTimeCandidates(text)

        val accepted = resolveOverlaps(candidates)

        data class Placed(val candidate: Candidate, val start: Int, val end: Int)

        var placed = accepted
            .map { c -> val (s, e) = expandGlue(text, c.range); Placed(c, s, e) }
            .sortedBy { it.start }

        // Safety net: glue expansion could in pathological input pull a span's
        // start back over territory another accepted span already owns. Clamp
        // rather than let two spans overlap in the final list.
        val clamped = mutableListOf<Placed>()
        var floor = 0
        for (p in placed) {
            val start = maxOf(p.start, floor)
            if (start >= p.end) continue
            clamped += Placed(p.candidate, start, p.end)
            floor = p.end
        }
        placed = clamped

        val repeatPiece = placed.firstOrNull { it.candidate.kind == Kind.REPEAT }?.candidate
        val dateTimePiece = placed.firstOrNull { it.candidate.kind == Kind.DATETIME }?.candidate
        val datePiece = placed.firstOrNull { it.candidate.kind == Kind.DATE }?.candidate
        val timePiece = placed.firstOrNull { it.candidate.kind == Kind.TIME }?.candidate

        val (dueAt, isAllDay) = combine(
            now = now,
            repeat = repeatPiece?.repeat,
            explicitDate = datePiece?.date,
            explicitTime = timePiece?.time,
            exactDateTime = dateTimePiece?.exact
        )

        val spans = placed.map { ParsedSpan(it.start, it.end) }
        val cleanedTitle = buildCleanedTitle(text, spans)

        return ParseResult(
            dueAt = dueAt,
            isAllDay = isAllDay,
            repeat = repeatPiece?.repeat,
            spans = spans,
            cleanedTitle = cleanedTitle
        )
    }

    private fun resolveOverlaps(candidates: List<Candidate>): List<Candidate> {
        val sorted = candidates.sortedWith(
            compareByDescending<Candidate> { it.range.last - it.range.first + 1 }.thenBy { it.range.first }
        )
        val accepted = mutableListOf<Candidate>()
        for (c in sorted) {
            val overlaps = accepted.any { it.range.first <= c.range.last && c.range.first <= it.range.last }
            if (!overlaps) accepted += c
        }
        return accepted
    }

    /** Grows [range] leftward over any run of glue words immediately preceding it. */
    private fun expandGlue(text: String, range: IntRange): Pair<Int, Int> {
        var start = range.first
        val end = range.last + 1
        while (true) {
            var i = start
            while (i > 0 && text[i - 1].isWhitespace()) i--
            val wordEnd = i
            var wordStart = wordEnd
            while (wordStart > 0 && text[wordStart - 1].isLetter()) wordStart--
            if (wordStart == wordEnd) break
            val word = text.substring(wordStart, wordEnd).lowercase()
            if (word !in GLUE_WORDS) break
            start = wordStart
        }
        return start to end
    }

    private fun buildCleanedTitle(text: String, spans: List<ParsedSpan>): String {
        if (spans.isEmpty()) return text.trim()
        val sb = StringBuilder()
        var idx = 0
        for (span in spans) {
            if (span.start > idx) sb.append(text, idx, span.start)
            idx = maxOf(idx, span.end)
        }
        if (idx < text.length) sb.append(text, idx, text.length)
        val cleaned = sb.toString().replace(Regex("\\s+"), " ").trim()
        return cleaned.ifEmpty { text.trim() }
    }

    // -----------------------------------------------------------------
    // Combining date + time + repeat into a final due moment
    // -----------------------------------------------------------------

    private fun combine(
        now: ZonedDateTime,
        repeat: RRule?,
        explicitDate: LocalDate?,
        explicitTime: LocalTime?,
        exactDateTime: ZonedDateTime?
    ): Pair<ZonedDateTime?, Boolean> {
        val effectiveDate = explicitDate ?: exactDateTime?.toLocalDate()
        val effectiveTime = explicitTime ?: exactDateTime?.toLocalTime()

        if (repeat != null && effectiveDate == null) {
            // No date was named explicitly, so the first due date is whatever
            // the rule derives — which means it must be checked against `now`
            // the same as any other derived/default value, not just when an
            // explicit time was also typed. Otherwise "every other week" typed
            // at 2pm would default to 9am *today*, already hours overdue.
            val today = now.toLocalDate()
            val derivedDate = deriveRepeatDate(repeat, today)
            val timeToUse = effectiveTime ?: ALL_DAY_DEFAULT_TIME
            val candidateDt = derivedDate.atTime(timeToUse).atZone(now.zone)
            val resolved = if (candidateDt.isAfter(now)) {
                candidateDt
            } else {
                // Already in the past (e.g. today's default 9am, or an
                // explicit time today that's gone by) — advance to the next
                // date the rule actually matches, not simply +1 day.
                RRuleExpander.nextAfter(repeat, candidateDt, now) ?: candidateDt
            }
            return resolved to (effectiveTime == null)
        }

        if (effectiveDate != null) {
            return finalizeDateTime(now, effectiveDate, effectiveTime)
        }

        if (effectiveTime != null) {
            val todayAtTime = now.toLocalDate().atTime(effectiveTime)
            val date = if (todayAtTime.isAfter(now.toLocalDateTime())) now.toLocalDate() else now.toLocalDate().plusDays(1)
            return finalizeDateTime(now, date, effectiveTime)
        }

        return null to false
    }

    private fun finalizeDateTime(now: ZonedDateTime, date: LocalDate, time: LocalTime?): Pair<ZonedDateTime?, Boolean> {
        return if (time != null) {
            date.atTime(time).atZone(now.zone) to false
        } else {
            date.atTime(ALL_DAY_DEFAULT_TIME).atZone(now.zone) to true
        }
    }

    /** Default time-of-day applied when a date was found but no time was. */
    private val ALL_DAY_DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    /** Earliest date >= [from] that satisfies [rrule]'s day-of-week / day-of-month constraint. */
    private fun deriveRepeatDate(rrule: RRule, from: LocalDate): LocalDate {
        return when (rrule.freq) {
            Freq.DAILY -> from
            Freq.WEEKLY -> {
                if (rrule.byDay.isEmpty()) {
                    from
                } else {
                    var d = from
                    var guard = 0
                    while (d.dayOfWeek !in rrule.byDay && guard < 8) {
                        d = d.plusDays(1)
                        guard++
                    }
                    d
                }
            }
            Freq.MONTHLY -> {
                val target = rrule.byMonthDay
                if (target == null) {
                    from
                } else {
                    var ym = YearMonth.from(from)
                    var result = from
                    var guard = 0
                    while (guard < 24) {
                        val dom = minOf(target, ym.lengthOfMonth())
                        val candidate = ym.atDay(dom)
                        if (!candidate.isBefore(from)) {
                            result = candidate
                            break
                        }
                        ym = ym.plusMonths(1)
                        guard++
                    }
                    result
                }
            }
            Freq.YEARLY -> {
                val month = rrule.byMonth
                if (month == null) {
                    from
                } else {
                    var year = from.year
                    var result = from
                    var guard = 0
                    while (guard < 5) {
                        val len = YearMonth.of(year, month).lengthOfMonth()
                        val dom = minOf(rrule.byMonthDay ?: from.dayOfMonth, len)
                        val candidate = LocalDate.of(year, month, dom)
                        if (!candidate.isBefore(from)) {
                            result = candidate
                            break
                        }
                        year++
                        guard++
                    }
                    result
                }
            }
        }
    }

    private fun nextStrictWeekday(from: LocalDate, dow: DayOfWeek): LocalDate {
        var d = from.plusDays(1)
        while (d.dayOfWeek != dow) d = d.plusDays(1)
        return d
    }

    private fun resolveMonthDay(today: LocalDate, month: Int, day: Int): LocalDate {
        var year = today.year
        var date = LocalDate.of(year, month, minOf(day, YearMonth.of(year, month).lengthOfMonth()))
        if (date.isBefore(today)) {
            year += 1
            date = LocalDate.of(year, month, minOf(day, YearMonth.of(year, month).lengthOfMonth()))
        }
        return date
    }

    private fun resolveDayOfMonth(today: LocalDate, day: Int): LocalDate {
        var ym = YearMonth.from(today)
        var dom = minOf(day, ym.lengthOfMonth())
        var candidate = ym.atDay(dom)
        if (candidate.isBefore(today)) {
            ym = ym.plusMonths(1)
            dom = minOf(day, ym.lengthOfMonth())
            candidate = ym.atDay(dom)
        }
        return candidate
    }

    private fun unitToFreq(unit: String): Freq? = when (unit.lowercase().trimEnd('s')) {
        "day" -> Freq.DAILY
        "week" -> Freq.WEEKLY
        "month" -> Freq.MONTHLY
        "year" -> Freq.YEARLY
        else -> null
    }

    // -----------------------------------------------------------------
    // Repeat phrase candidates
    // -----------------------------------------------------------------

    private fun findRepeatCandidates(text: String): List<Candidate> {
        val out = mutableListOf<Candidate>()

        Regex("""\bevery\s+month\s+on\s+the\s+(\d{1,2})(?:st|nd|rd|th)?\b""", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { m ->
                val day = m.groupValues[1].toIntOrNull()
                if (day != null && day in 1..31) {
                    out += Candidate(m.range, Kind.REPEAT, repeat = RRule(Freq.MONTHLY, byMonthDay = day))
                }
            }

        Regex("""\bevery\s+(\d{1,2})(?:st|nd|rd|th)\b""", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { m ->
                val day = m.groupValues[1].toIntOrNull()
                if (day != null && day in 1..31) {
                    out += Candidate(m.range, Kind.REPEAT, repeat = RRule(Freq.MONTHLY, byMonthDay = day))
                }
            }

        Regex("""\bevery\s+other\s+(day|week|month|year)\b""", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { m ->
                val freq = unitToFreq(m.groupValues[1])
                if (freq != null) out += Candidate(m.range, Kind.REPEAT, repeat = RRule(freq, interval = 2))
            }

        Regex("""\bevery\s+(\d+)\s+(days?|weeks?|months?|years?)\b""", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { m ->
                val n = m.groupValues[1].toIntOrNull()
                val freq = unitToFreq(m.groupValues[2])
                if (n != null && n >= 1 && freq != null) {
                    out += Candidate(m.range, Kind.REPEAT, repeat = RRule(freq, interval = n))
                }
            }

        Regex("""\bevery\s+weekday\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(
                m.range, Kind.REPEAT,
                repeat = RRule(
                    Freq.WEEKLY,
                    byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                )
            )
        }

        Regex("""\bevery\s+weekend\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(
                m.range, Kind.REPEAT,
                repeat = RRule(Freq.WEEKLY, byDay = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
            )
        }

        Regex("""\bevery\s+($WEEKDAY_ALT)(?:\s+and\s+($WEEKDAY_ALT))*\b""", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { m ->
                val days = Regex(WEEKDAY_ALT, RegexOption.IGNORE_CASE).findAll(m.value)
                    .mapNotNull { WEEKDAY_TOKENS[it.value.lowercase()] }
                    .toSet()
                if (days.isNotEmpty()) {
                    out += Candidate(m.range, Kind.REPEAT, repeat = RRule(Freq.WEEKLY, byDay = days))
                }
            }

        Regex("""\b(?:every\s+day|daily)\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.REPEAT, repeat = RRule(Freq.DAILY))
        }
        Regex("""\b(?:every\s+week|weekly)\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.REPEAT, repeat = RRule(Freq.WEEKLY))
        }
        Regex("""\b(?:every\s+month|monthly)\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.REPEAT, repeat = RRule(Freq.MONTHLY))
        }
        Regex("""\b(?:every\s+year|yearly|annually)\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.REPEAT, repeat = RRule(Freq.YEARLY))
        }

        return out
    }

    // -----------------------------------------------------------------
    // Date phrase candidates
    // -----------------------------------------------------------------

    private fun findDateCandidates(text: String, now: ZonedDateTime): List<Candidate> {
        val out = mutableListOf<Candidate>()
        val today = now.toLocalDate()

        Regex("""\bday\s+after\s+tomorrow\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.DATE, date = today.plusDays(2))
        }
        Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.DATE, date = today.plusDays(1))
        }
        Regex("""\btmrw\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.DATE, date = today.plusDays(1))
        }
        Regex("""\btoday\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.DATE, date = today)
        }
        Regex("""\bnext\s+week\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.DATE, date = today.plusWeeks(1))
        }
        Regex("""\bnext\s+month\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.DATE, date = today.plusMonths(1))
        }

        Regex("""\b(?:(?:this|next)\s+)?($WEEKDAY_ALT)\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            val dow = WEEKDAY_TOKENS[m.groupValues[1].lowercase()]
            if (dow != null) {
                out += Candidate(m.range, Kind.DATE, date = nextStrictWeekday(today, dow))
            }
        }

        Regex("""\bon\s+the\s+(\d{1,2})(?:st|nd|rd|th)?\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            val day = m.groupValues[1].toIntOrNull()
            if (day != null && day in 1..31) {
                out += Candidate(m.range, Kind.DATE, date = resolveDayOfMonth(today, day))
            }
        }

        Regex("""\bon\s+(\d{1,2})(?:st|nd|rd|th)?\s+($MONTH_ALT)\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            val day = m.groupValues[1].toIntOrNull()
            val month = MONTH_TOKENS[m.groupValues[2].lowercase()]
            if (day != null && month != null) out += Candidate(m.range, Kind.DATE, date = resolveMonthDay(today, month, day))
        }
        Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+($MONTH_ALT)\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            val day = m.groupValues[1].toIntOrNull()
            val month = MONTH_TOKENS[m.groupValues[2].lowercase()]
            if (day != null && month != null) out += Candidate(m.range, Kind.DATE, date = resolveMonthDay(today, month, day))
        }
        Regex("""\b($MONTH_ALT)\s+(\d{1,2})(?:st|nd|rd|th)?\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            val month = MONTH_TOKENS[m.groupValues[1].lowercase()]
            val day = m.groupValues[2].toIntOrNull()
            if (day != null && month != null) out += Candidate(m.range, Kind.DATE, date = resolveMonthDay(today, month, day))
        }

        Regex("""\bin\s+(\d+)\s+(days?|weeks?)\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            val n = m.groupValues[1].toIntOrNull()
            val unit = m.groupValues[2].lowercase()
            if (n != null) {
                val date = if (unit.startsWith("week")) today.plusWeeks(n.toLong()) else today.plusDays(n.toLong())
                out += Candidate(m.range, Kind.DATE, date = date)
            }
        }

        Regex("""\bin\s+(a|an|\d+)\s+(hours?|minutes?)\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            val raw = m.groupValues[1].lowercase()
            val n = if (raw == "a" || raw == "an") 1L else raw.toLongOrNull()
            val unit = m.groupValues[2].lowercase()
            if (n != null) {
                val exact = if (unit.startsWith("hour")) now.plusHours(n) else now.plusMinutes(n)
                out += Candidate(m.range, Kind.DATETIME, exact = exact)
            }
        }

        Regex("""\btonight\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            val exact = today.atTime(19, 0).atZone(now.zone)
            out += Candidate(m.range, Kind.DATETIME, exact = exact)
        }

        return out
    }

    // -----------------------------------------------------------------
    // Time phrase candidates
    // -----------------------------------------------------------------

    private fun findTimeCandidates(text: String): List<Candidate> {
        val out = mutableListOf<Candidate>()

        Regex("""\b(?:at\s+)?(1[0-2]|0?[1-9])(?::([0-5][0-9]))?\s?(am|pm)\b""", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { m ->
                val hour12 = m.groupValues[1].toInt()
                val minute = m.groupValues[2].toIntOrNull() ?: 0
                val ampm = m.groupValues[3].lowercase()
                var hour = hour12 % 12
                if (ampm == "pm") hour += 12
                out += Candidate(m.range, Kind.TIME, time = LocalTime.of(hour, minute))
            }

        Regex("""\b(?:at\s+)?([01]?[0-9]|2[0-3]):([0-5][0-9])\b""", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { m ->
                val hour = m.groupValues[1].toInt()
                val minute = m.groupValues[2].toInt()
                out += Candidate(m.range, Kind.TIME, time = LocalTime.of(hour, minute))
            }

        Regex("""\bat\s+([01]?[0-9]|2[0-3])\b""", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { m ->
                val hour = m.groupValues[1].toInt()
                out += Candidate(m.range, Kind.TIME, time = LocalTime.of(hour, 0))
            }

        Regex("""\b(?:noon|midday)\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.TIME, time = LocalTime.of(12, 0))
        }
        Regex("""\bmidnight\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.TIME, time = LocalTime.of(0, 0))
        }
        Regex("""\bmorning\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.TIME, time = LocalTime.of(9, 0))
        }
        Regex("""\bafternoon\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.TIME, time = LocalTime.of(14, 0))
        }
        Regex("""\bevening\b""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
            out += Candidate(m.range, Kind.TIME, time = LocalTime.of(18, 0))
        }

        return out
    }
}
