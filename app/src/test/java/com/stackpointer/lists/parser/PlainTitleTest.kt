package com.stackpointer.lists.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Guards against the parser quietly eating words out of a title.
 *
 * Written after two reminders typed over adb came back with words missing,
 * which looked exactly like a parser bug. It wasn't — `adb shell input text`
 * was dropping everything after a space — but "the app ate my title" is a
 * failure that would be easy to miss and very hard to explain, so the guard
 * stays.
 */
class PlainTitleTest {

    private val now: ZonedDateTime =
        ZonedDateTime.of(2026, 8, 18, 16, 30, 0, 0, ZoneId.of("Europe/Lisbon"))

    @Test
    fun `ordinary words are not stripped from a title`() {
        listOf(
            "Overdue line check",
            "Post the letter",
            "Call the dentist about the appointment"
        ).forEach { text ->
            assertEquals(text, CaptureParser.parse(text, now).cleanedTitle)
        }
    }

    /**
     * Dictated text arrives as one unpunctuated run of words, which is exactly
     * the shape the parser has the least help with. The emulator has no
     * microphone, so this is the only way to cover what voice capture hands
     * over.
     */
    @Test
    fun `spoken-style phrases still yield a date and a clean title`() {
        val tomorrow = CaptureParser.parse("call mum tomorrow at six", now)
        assertNotNull("expected a due date from 'tomorrow at six'", tomorrow.dueAt)
        assertEquals("call mum", tomorrow.cleanedTitle)

        val weekly = CaptureParser.parse("water the plants every monday", now)
        assertNotNull("expected a repeat rule from 'every monday'", weekly.repeat)
        assertEquals("water the plants", weekly.cleanedTitle)
    }
}
