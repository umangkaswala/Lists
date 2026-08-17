package com.stackpointer.lists.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RRuleTextTest {

    // A generic Thursday, used where startDate doesn't affect the result.
    private val genericDate = LocalDate.of(2026, 1, 1)

    // 6 Jan 2026 is a Tuesday — used for WEEKLY rules that default BYDAY from startDate.
    private val tuesday = LocalDate.of(2026, 1, 6)

    // --- rruleSummary: base clause -----------------------------------------

    @Test
    fun `daily summary`() {
        assertEquals("Daily", rruleSummary(RRule(Freq.DAILY), genericDate))
        assertEquals("Every 3 days", rruleSummary(RRule(Freq.DAILY, interval = 3), genericDate))
    }

    @Test
    fun `weekly summary defaults byDay from startDate when omitted`() {
        assertEquals("Every Tuesday", rruleSummary(RRule(Freq.WEEKLY), tuesday))
    }

    @Test
    fun `weekly summary with multiple byDay joins with Oxford-comma-free and`() {
        val rule = RRule(Freq.WEEKLY, byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertEquals("Every Monday, Wednesday and Friday", rruleSummary(rule, genericDate))
    }

    @Test
    fun `weekly summary with interval and byDay and until clause`() {
        val rule = RRule(
            Freq.WEEKLY,
            interval = 2,
            byDay = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
            until = LocalDate.of(2026, 12, 31)
        )
        assertEquals(
            "Every 2 weeks on Tuesday and Friday, until 31 Dec 2026",
            rruleSummary(rule, genericDate)
        )
    }

    @Test
    fun `monthly summary with and without byMonthDay`() {
        assertEquals("Monthly on day 15", rruleSummary(RRule(Freq.MONTHLY, byMonthDay = 15), genericDate))
        assertEquals("Every 2 months", rruleSummary(RRule(Freq.MONTHLY, interval = 2), genericDate))
    }

    @Test
    fun `yearly summary with byMonth and byMonthDay`() {
        val rule = RRule(Freq.YEARLY, byMonth = 12, byMonthDay = 25)
        assertEquals("Yearly on 25 December", rruleSummary(rule, genericDate))
    }

    @Test
    fun `yearly summary with interval byMonth byMonthDay and count clause`() {
        val rule = RRule(Freq.YEARLY, interval = 5, byMonth = 12, byMonthDay = 25, count = 10)
        assertEquals(
            "Every 5 years on 25 December, 10 times",
            rruleSummary(rule, genericDate)
        )
    }

    // --- rruleSummary: ending clause -----------------------------------------

    @Test
    fun `summary with count only`() {
        assertEquals("Daily, 10 times", rruleSummary(RRule(Freq.DAILY, count = 10), genericDate))
    }

    @Test
    fun `summary with until only`() {
        val rule = RRule(Freq.DAILY, until = LocalDate.of(2026, 12, 31))
        assertEquals("Daily, until 31 Dec 2026", rruleSummary(rule, genericDate))
    }

    @Test
    fun `summary with neither until nor count has no ending clause`() {
        assertEquals("Daily", rruleSummary(RRule(Freq.DAILY), genericDate))
    }

    // --- rruleShortLabel ---------------------------------------------------

    @Test
    fun `short label examples`() {
        assertEquals("Every Tuesday", rruleShortLabel(RRule(Freq.WEEKLY), tuesday))
        assertEquals(
            "Every 2 weeks",
            rruleShortLabel(RRule(Freq.WEEKLY, interval = 2, byDay = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)), genericDate)
        )
        assertEquals("Daily", rruleShortLabel(RRule(Freq.DAILY), genericDate))
        assertEquals("Monthly", rruleShortLabel(RRule(Freq.MONTHLY), genericDate))
    }

    @Test
    fun `short label omits byMonthDay byMonth qualifiers`() {
        assertEquals("Monthly", rruleShortLabel(RRule(Freq.MONTHLY, byMonthDay = 15), genericDate))
        assertEquals(
            "Yearly",
            rruleShortLabel(RRule(Freq.YEARLY, byMonth = 12, byMonthDay = 25), genericDate)
        )
    }

    @Test
    fun `short label omits until and count ending clause`() {
        assertEquals("Daily", rruleShortLabel(RRule(Freq.DAILY, count = 5), genericDate))
        assertEquals(
            "Daily",
            rruleShortLabel(RRule(Freq.DAILY, until = LocalDate.of(2026, 12, 31)), genericDate)
        )
    }
}
