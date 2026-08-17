package com.stackpointer.lists.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RRuleParseTest {

    @Test
    fun `round trip daily`() {
        val rule = RRule(Freq.DAILY)
        assertEquals("FREQ=DAILY", rule.toRRuleString())
        assertEquals(rule, RRule.parse(rule.toRRuleString()))
    }

    @Test
    fun `round trip weekly with byDay and interval`() {
        val rule = RRule(Freq.WEEKLY, interval = 2, byDay = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,FR", rule.toRRuleString())
        assertEquals(rule, RRule.parse(rule.toRRuleString()))
    }

    @Test
    fun `round trip weekly byDay order is stable regardless of set construction order`() {
        val rule = RRule(Freq.WEEKLY, byDay = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR", rule.toRRuleString())
        assertEquals(rule, RRule.parse(rule.toRRuleString()))
    }

    @Test
    fun `round trip monthly with byMonthDay and until`() {
        val rule = RRule(Freq.MONTHLY, byMonthDay = 15, until = LocalDate.of(2026, 12, 31))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=15;UNTIL=20261231", rule.toRRuleString())
        assertEquals(rule, RRule.parse(rule.toRRuleString()))
    }

    @Test
    fun `round trip yearly with byMonth byMonthDay and count`() {
        val rule = RRule(Freq.YEARLY, byMonth = 12, byMonthDay = 25, count = 5)
        assertEquals("FREQ=YEARLY;BYMONTHDAY=25;BYMONTH=12;COUNT=5", rule.toRRuleString())
        assertEquals(rule, RRule.parse(rule.toRRuleString()))
    }

    @Test
    fun `parse accepts UNTIL with a date-time form`() {
        val rule = RRule.parse("FREQ=DAILY;UNTIL=20261231T235959Z")
        assertEquals(LocalDate.of(2026, 12, 31), rule?.until)
    }

    @Test
    fun `parse returns null for null or blank input`() {
        assertNull(RRule.parse(null))
        assertNull(RRule.parse(""))
        assertNull(RRule.parse("   "))
    }

    @Test
    fun `parse returns null for garbage`() {
        assertNull(RRule.parse("not a rule at all"))
    }

    @Test
    fun `parse returns null for missing FREQ`() {
        assertNull(RRule.parse("INTERVAL=2;BYDAY=MO"))
    }

    @Test
    fun `parse returns null for unknown FREQ`() {
        assertNull(RRule.parse("FREQ=SECONDLY"))
    }

    @Test
    fun `parse returns null for bad BYDAY token`() {
        assertNull(RRule.parse("FREQ=WEEKLY;BYDAY=XX"))
    }

    @Test
    fun `parse returns null when COUNT and UNTIL are both present`() {
        assertNull(RRule.parse("FREQ=DAILY;COUNT=5;UNTIL=20261231"))
    }

    @Test
    fun `parse returns null for interval below 1`() {
        assertNull(RRule.parse("FREQ=DAILY;INTERVAL=0"))
    }

    @Test
    fun `parse returns null for out of range BYMONTHDAY`() {
        assertNull(RRule.parse("FREQ=MONTHLY;BYMONTHDAY=32"))
    }

    @Test
    fun `parse returns null for out of range BYMONTH`() {
        assertNull(RRule.parse("FREQ=YEARLY;BYMONTH=13"))
    }

    @Test
    fun `parse returns null for count below 1`() {
        assertNull(RRule.parse("FREQ=DAILY;COUNT=0"))
    }

    @Test
    fun `parse returns null for malformed segment without equals sign`() {
        assertNull(RRule.parse("FREQ=DAILY;GARBAGE"))
    }
}
