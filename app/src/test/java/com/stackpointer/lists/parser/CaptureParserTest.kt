package com.stackpointer.lists.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CaptureParserTest {

    // Monday 17 Aug 2026, 10:00 local.
    private val zone: ZoneId = ZoneId.of("Europe/Lisbon")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 8, 17, 10, 0, 0, 0, zone)

    private fun dt(y: Int, mo: Int, d: Int, h: Int, mi: Int): ZonedDateTime =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    @Test
    fun `design example - bins out every tuesday at 7pm`() {
        val result = CaptureParser.parse("Bins out every Tuesday at 7pm", now)
        assertEquals("Bins out", result.cleanedTitle)
        assertEquals(dt(2026, 8, 18, 19, 0), result.dueAt)
        assertFalse(result.isAllDay)
        assertNotNull(result.repeat)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", result.repeat!!.toRRuleString())
    }

    @Test
    fun `buy milk tomorrow morning`() {
        val result = CaptureParser.parse("Buy milk tomorrow morning", now)
        assertEquals("Buy milk", result.cleanedTitle)
        assertEquals(dt(2026, 8, 18, 9, 0), result.dueAt)
        assertNull(result.repeat)
    }

    @Test
    fun `team meeting friday 10am`() {
        val result = CaptureParser.parse("Team meeting Friday 10am", now)
        assertEquals("Team meeting", result.cleanedTitle)
        assertEquals(dt(2026, 8, 21, 10, 0), result.dueAt)
        assertFalse(result.isAllDay)
    }

    @Test
    fun `call mum at 7pm`() {
        val result = CaptureParser.parse("Call mum at 7pm", now)
        assertEquals("Call mum", result.cleanedTitle)
        // "at 7pm" today is still in the future relative to 10:00 now, so today.
        assertEquals(dt(2026, 8, 17, 19, 0), result.dueAt)
        assertFalse(result.isAllDay)
    }

    @Test
    fun `pay rent every month on the 1st`() {
        val result = CaptureParser.parse("Pay rent every month on the 1st", now)
        assertEquals("Pay rent", result.cleanedTitle)
        assertEquals(dt(2026, 9, 1, 9, 0), result.dueAt)
        assertTrue(result.isAllDay)
        assertNotNull(result.repeat)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=1", result.repeat!!.toRRuleString())
    }

    @Test
    fun `standup every weekday at 9_30`() {
        val result = CaptureParser.parse("Standup every weekday at 9:30", now)
        assertEquals("Standup", result.cleanedTitle)
        // Monday's 9:30 has already passed (now is 10:00), so the next weekday is Tuesday.
        assertEquals(dt(2026, 8, 18, 9, 30), result.dueAt)
        assertFalse(result.isAllDay)
        assertNotNull(result.repeat)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", result.repeat!!.toRRuleString())
    }

    @Test
    fun `gym every other week`() {
        val result = CaptureParser.parse("Gym every other week", now)
        assertEquals("Gym", result.cleanedTitle)
        // now is 10:00, so the naive default of "today at 09:00" is already
        // in the past — it must roll forward to the next matching week
        // (every OTHER week, so +2 weeks: 31 Aug), not just +1 day.
        assertEquals(dt(2026, 8, 31, 9, 0), result.dueAt)
        assertTrue(result.isAllDay)
        assertNotNull(result.repeat)
        assertEquals("FREQ=WEEKLY;INTERVAL=2", result.repeat!!.toRRuleString())
    }

    @Test
    fun `repeat-derived default time already past today rolls to next occurrence`() {
        // Regression test: typing a no-time repeat phrase in the afternoon
        // must not silently produce a due date/time that's already overdue
        // the moment the reminder is created.
        val afternoon = ZonedDateTime.of(2026, 8, 17, 14, 0, 0, 0, zone)
        val result = CaptureParser.parse("Gym every other week", afternoon)
        assertEquals("Gym", result.cleanedTitle)
        assertTrue(result.isAllDay)
        assertNotNull(result.dueAt)
        assertTrue(result.dueAt!!.isAfter(afternoon))
        assertEquals(dt(2026, 8, 31, 9, 0), result.dueAt)
    }

    @Test
    fun `dentist in 3 days`() {
        val result = CaptureParser.parse("Dentist in 3 days", now)
        assertEquals("Dentist", result.cleanedTitle)
        assertEquals(dt(2026, 8, 20, 9, 0), result.dueAt)
        assertTrue(result.isAllDay)
        assertNull(result.repeat)
    }

    @Test
    fun `party on 25 dec`() {
        val result = CaptureParser.parse("Party on 25 Dec", now)
        assertEquals("Party", result.cleanedTitle)
        assertEquals(dt(2026, 12, 25, 9, 0), result.dueAt)
        assertTrue(result.isAllDay)
    }

    // --- Word boundary negatives -------------------------------------

    @Test
    fun `satisfied does not match sat`() {
        val result = CaptureParser.parse("Satisfied with the result", now)
        assertEquals("Satisfied with the result", result.cleanedTitle)
        assertNull(result.dueAt)
        assertNull(result.repeat)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `market does not match mar`() {
        val result = CaptureParser.parse("Go to the market", now)
        assertEquals("Go to the market", result.cleanedTitle)
        assertNull(result.dueAt)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `send invoice matches nothing`() {
        val result = CaptureParser.parse("Send invoice", now)
        assertEquals("Send invoice", result.cleanedTitle)
        assertNull(result.dueAt)
        assertNull(result.repeat)
        assertTrue(result.spans.isEmpty())
    }

    // --- Whole-input-is-a-date fallback --------------------------------

    @Test
    fun `whole input is a date phrase keeps the title`() {
        val result = CaptureParser.parse("tomorrow", now)
        assertEquals("tomorrow", result.cleanedTitle)
        assertEquals(dt(2026, 8, 18, 9, 0), result.dueAt)
        assertTrue(result.isAllDay)
    }

    // --- Empty / whitespace input never crashes -------------------------

    @Test
    fun `empty input does not crash`() {
        val result = CaptureParser.parse("", now)
        assertEquals("", result.cleanedTitle)
        assertNull(result.dueAt)
        assertNull(result.repeat)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `whitespace only input does not crash`() {
        val result = CaptureParser.parse("   ", now)
        assertEquals("", result.cleanedTitle)
        assertNull(result.dueAt)
    }

    // --- Extra phrase coverage -------------------------------------------

    @Test
    fun `every tue and fri combines both days`() {
        val result = CaptureParser.parse("Take out bins every Tue and Fri", now)
        assertEquals("Take out bins", result.cleanedTitle)
        assertNotNull(result.repeat)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,FR", result.repeat!!.toRRuleString())
    }

    @Test
    fun `next monday is accepted with this-next glue absorbed`() {
        val result = CaptureParser.parse("Submit report next Monday", now)
        assertEquals("Submit report", result.cleanedTitle)
        // Next strictly-future Monday from Mon 17 Aug 2026 is 24 Aug 2026.
        assertEquals(dt(2026, 8, 24, 9, 0), result.dueAt)
        assertTrue(result.isAllDay)
    }

    @Test
    fun `in an hour produces an exact moment not all day`() {
        val result = CaptureParser.parse("Check oven in an hour", now)
        assertEquals("Check oven", result.cleanedTitle)
        assertEquals(dt(2026, 8, 17, 11, 0), result.dueAt)
        assertFalse(result.isAllDay)
    }

    @Test
    fun `in 30 minutes produces an exact moment`() {
        val result = CaptureParser.parse("Take pills in 30 minutes", now)
        assertEquals("Take pills", result.cleanedTitle)
        assertEquals(dt(2026, 8, 17, 10, 30), result.dueAt)
        assertFalse(result.isAllDay)
    }

    @Test
    fun `24 hour clock time is read literally`() {
        val result = CaptureParser.parse("Flight check-in 19:30", now)
        assertEquals("Flight check-in", result.cleanedTitle)
        assertEquals(dt(2026, 8, 17, 19, 30), result.dueAt)
        assertFalse(result.isAllDay)
    }

    @Test
    fun `noon and midnight resolve to fixed times`() {
        val noonResult = CaptureParser.parse("Lunch at noon", now)
        assertEquals(dt(2026, 8, 17, 12, 0), noonResult.dueAt)

        val midnightResult = CaptureParser.parse("Backup job at midnight", now)
        assertEquals(dt(2026, 8, 18, 0, 0), midnightResult.dueAt)
    }

    @Test
    fun `day after tomorrow beats a nested tomorrow match`() {
        val result = CaptureParser.parse("Renew passport day after tomorrow", now)
        assertEquals("Renew passport", result.cleanedTitle)
        assertEquals(dt(2026, 8, 19, 9, 0), result.dueAt)
        assertTrue(result.isAllDay)
    }

    @Test
    fun `every 2 weeks beats a nested every week match`() {
        val result = CaptureParser.parse("Water plants every 2 weeks", now)
        assertNotNull(result.repeat)
        assertEquals("FREQ=WEEKLY;INTERVAL=2", result.repeat!!.toRRuleString())
        assertEquals("Water plants", result.cleanedTitle)
    }

    @Test
    fun `formatDueChip matches design example`() {
        val label = formatDueChip(dt(2026, 8, 18, 19, 0), isAllDay = false)
        assertEquals("Tue, 18 Aug, 7:00 pm", label)
    }

    @Test
    fun `formatDueChip for all-day omits time`() {
        val label = formatDueChip(dt(2026, 8, 18, 9, 0), isAllDay = true)
        assertEquals("Tue, 18 Aug", label)
    }
}
