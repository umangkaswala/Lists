package com.stackpointer.lists.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RRuleExpanderTest {

    private val utc = ZoneId.of("UTC")

    private fun zdt(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0, zone: ZoneId = utc): ZonedDateTime =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    // --- DAILY ---------------------------------------------------------

    @Test
    fun `daily interval 1 basic expansion`() {
        // 1 Jan 2026 is a Thursday.
        val start = zdt(2026, 1, 1)
        val rule = RRule(Freq.DAILY)
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 3)
        assertEquals(listOf(zdt(2026, 1, 1), zdt(2026, 1, 2), zdt(2026, 1, 3)), result)
    }

    @Test
    fun `daily interval 3`() {
        val start = zdt(2026, 1, 1)
        val rule = RRule(Freq.DAILY, interval = 3)
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 3)
        assertEquals(listOf(zdt(2026, 1, 1), zdt(2026, 1, 4), zdt(2026, 1, 7)), result)
    }

    // --- WEEKLY ----------------------------------------------------------

    @Test
    fun `weekly with no byDay repeats on start weekday every interval weeks`() {
        // 1 Jan 2026 is a Thursday.
        val start = zdt(2026, 1, 1)
        val rule = RRule(Freq.WEEKLY)
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 3)
        assertEquals(listOf(zdt(2026, 1, 1), zdt(2026, 1, 8), zdt(2026, 1, 15)), result)
    }

    @Test
    fun `weekly with byDay across a week boundary`() {
        // 6 Jan 2026 is a Tuesday; 9 Jan 2026 is a Friday.
        val start = zdt(2026, 1, 6)
        val rule = RRule(Freq.WEEKLY, byDay = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY))
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 4)
        assertEquals(
            listOf(zdt(2026, 1, 6), zdt(2026, 1, 9), zdt(2026, 1, 13), zdt(2026, 1, 16)),
            result
        )
    }

    @Test
    fun `weekly with multiple byDay and interval 2 skips the intervening week entirely`() {
        // 5 Jan 2026 is a Monday (week: Mon 5 / Wed 7 / Fri 9).
        val start = zdt(2026, 1, 5)
        val rule = RRule(
            Freq.WEEKLY,
            interval = 2,
            byDay = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        )
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 6)
        // Week of 12 Jan is skipped entirely (interval = 2); next active week starts 19 Jan.
        assertEquals(
            listOf(
                zdt(2026, 1, 5), zdt(2026, 1, 7), zdt(2026, 1, 9),
                zdt(2026, 1, 19), zdt(2026, 1, 21), zdt(2026, 1, 23)
            ),
            result
        )
    }

    @Test
    fun `weekly with byDay not matching start still starts from the first matching day`() {
        // 1 Jan 2026 (Thursday) with BYDAY=TU: first match should be 6 Jan (Tuesday).
        val start = zdt(2026, 1, 1)
        val rule = RRule(Freq.WEEKLY, byDay = setOf(DayOfWeek.TUESDAY))
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 2)
        assertEquals(listOf(zdt(2026, 1, 6), zdt(2026, 1, 13)), result)
    }

    // --- MONTHLY -----------------------------------------------------------

    @Test
    fun `monthly with default day-of-month`() {
        val start = zdt(2026, 1, 15)
        val rule = RRule(Freq.MONTHLY)
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 3)
        assertEquals(listOf(zdt(2026, 1, 15), zdt(2026, 2, 15), zdt(2026, 3, 15)), result)
    }

    @Test
    fun `monthly interval 2`() {
        val start = zdt(2026, 1, 10)
        val rule = RRule(Freq.MONTHLY, interval = 2)
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 3)
        assertEquals(listOf(zdt(2026, 1, 10), zdt(2026, 3, 10), zdt(2026, 5, 10)), result)
    }

    @Test
    fun `monthly from 31 Jan skips February and lands on 31 Mar, not clamped to 28 Feb`() {
        val start = zdt(2026, 1, 31)
        val rule = RRule(Freq.MONTHLY)
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 2)
        assertEquals(listOf(zdt(2026, 1, 31), zdt(2026, 3, 31)), result)

        val next = RRuleExpander.nextAfter(rule, start, after = start)
        assertEquals(zdt(2026, 3, 31), next)
    }

    // --- YEARLY --------------------------------------------------------------

    @Test
    fun `yearly with default month and day`() {
        val start = zdt(2026, 3, 15)
        val rule = RRule(Freq.YEARLY)
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 3)
        assertEquals(listOf(zdt(2026, 3, 15), zdt(2027, 3, 15), zdt(2028, 3, 15)), result)
    }

    @Test
    fun `yearly interval 5 with explicit byMonth and byMonthDay`() {
        val start = zdt(2026, 1, 1)
        val rule = RRule(Freq.YEARLY, interval = 5, byMonth = 12, byMonthDay = 25)
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 2)
        assertEquals(listOf(zdt(2026, 12, 25), zdt(2031, 12, 25)), result)
    }

    @Test
    fun `yearly leap day skips non-leap years, next occurrence is 29 Feb 2028`() {
        val start = zdt(2024, 2, 29)
        val rule = RRule(Freq.YEARLY)
        val next = RRuleExpander.nextAfter(rule, start, after = start)
        assertEquals(zdt(2028, 2, 29), next)

        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 2)
        assertEquals(listOf(zdt(2024, 2, 29), zdt(2028, 2, 29)), result)
    }

    // --- UNTIL / COUNT -----------------------------------------------------

    @Test
    fun `until boundary includes the occurrence on the until date but not after`() {
        val start = zdt(2026, 1, 1)
        val rule = RRule(Freq.DAILY, until = LocalDate.of(2026, 1, 3))
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 10)
        assertEquals(listOf(zdt(2026, 1, 1), zdt(2026, 1, 2), zdt(2026, 1, 3)), result)

        val next = RRuleExpander.nextAfter(rule, start, after = zdt(2026, 1, 3))
        assertNull(next)
    }

    @Test
    fun `count caps the series and nextAfter returns null once exhausted`() {
        val start = zdt(2026, 1, 1)
        val rule = RRule(Freq.DAILY, count = 3)
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 10)
        assertEquals(listOf(zdt(2026, 1, 1), zdt(2026, 1, 2), zdt(2026, 1, 3)), result)

        val next = RRuleExpander.nextAfter(rule, start, after = zdt(2026, 1, 3))
        assertNull(next)
    }

    @Test
    fun `nextAfter finds the first occurrence strictly after the given time`() {
        val start = zdt(2026, 1, 1)
        val rule = RRule(Freq.DAILY)
        val next = RRuleExpander.nextAfter(rule, start, after = zdt(2026, 1, 1))
        assertEquals(zdt(2026, 1, 2), next)
    }

    // --- DST -----------------------------------------------------------------

    @Test
    fun `daily 9am reminder stays 9am local across the Lisbon spring-forward transition`() {
        val lisbon = ZoneId.of("Europe/Lisbon")
        // Clocks spring forward in Lisbon on 29 Mar 2026 (last Sunday of March).
        val start = zdt(2026, 3, 28, zone = lisbon) // Saturday, before the transition
        val rule = RRule(Freq.DAILY)
        val result = RRuleExpander.occurrencesFrom(rule, start, limit = 3)

        assertEquals(3, result.size)
        assertEquals(LocalDate.of(2026, 3, 28), result[0].toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 29), result[1].toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 30), result[2].toLocalDate())
        result.forEach { assertEquals(LocalTime.of(9, 0), it.toLocalTime()) }

        // The offset really did change across the transition (proving this
        // isn't trivially true because 9am never crosses the gap): WET is
        // UTC+0, WEST is UTC+1.
        assertEquals(ZoneOffset.ofHours(0), result[0].offset)
        assertEquals(ZoneOffset.ofHours(1), result[2].offset)
    }

    // --- guard rails -----------------------------------------------------

    @Test
    fun `occurrencesFrom with non-positive limit returns empty list`() {
        val start = zdt(2026, 1, 1)
        val rule = RRule(Freq.DAILY)
        assertTrue(RRuleExpander.occurrencesFrom(rule, start, limit = 0).isEmpty())
    }

    @Test(timeout = 5000)
    fun `monthly byMonthDay 31 pinned to February by a 12-month interval never yields and terminates`() {
        // Every candidate month is February (interval = 12 from a February
        // anchor), and no February has a 31st: the "skip, don't clamp" rule
        // rejects every single candidate, forever. This must still return
        // instead of spinning the search cap forever with nothing to count.
        val start = zdt(2026, 2, 1)
        val rule = RRule(Freq.MONTHLY, interval = 12, byMonthDay = 31)

        assertTrue(RRuleExpander.occurrencesFrom(rule, start, limit = 5).isEmpty())
        assertNull(RRuleExpander.nextAfter(rule, start, after = start))
    }

    @Test(timeout = 5000)
    fun `yearly 30 February never yields and terminates`() {
        // 30 Feb never exists in any year, leap or not: every candidate is
        // rejected, forever.
        val start = zdt(2026, 1, 1)
        val rule = RRule(Freq.YEARLY, byMonth = 2, byMonthDay = 30)

        assertTrue(RRuleExpander.occurrencesFrom(rule, start, limit = 5).isEmpty())
        assertNull(RRuleExpander.nextAfter(rule, start, after = start))
    }
}
