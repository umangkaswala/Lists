package com.stackpointer.lists.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PlainTitleTest {
    @Test
    fun `ordinary words are not stripped from a title`() {
        val now = ZonedDateTime.of(2026, 8, 18, 16, 30, 0, 0, ZoneId.of("Europe/Lisbon"))
        listOf(
            "Overdue line check",
            "Post the letter",
            "Call the dentist about the appointment"
        ).forEach { text ->
            val parsed = CaptureParser.parse(text, now)
            assertEquals(text, parsed.cleanedTitle)
        }
    }
}
