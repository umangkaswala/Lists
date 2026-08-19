package com.stackpointer.lists.capture

import com.stackpointer.lists.data.prefs.QuickTimeSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The quick-time chips are labelled from the settings behind them, so the
 * labels and the instants have to agree. These are the rules that can go wrong
 * silently: a chip that says "Tonight" while setting a time that has already
 * gone produces an alarm in the past, which never fires at all.
 */
class QuickTimePresetsTest {

    private val zone: ZoneId = ZoneId.of("Europe/Lisbon")

    private fun at(text: String): ZonedDateTime =
        LocalDateTime.parse(text).atZone(zone)

    private fun labels(settings: QuickTimeSettings, now: String): List<String> =
        quickTimePresets(settings, at(now)).map { it.label }

    @Test
    fun `default chips read as the design draws them`() {
        assertEquals(
            listOf("In 1 hour", "Tonight 7 pm", "Tomorrow 9 am"),
            labels(QuickTimeSettings(), "2026-08-19T10:00")
        )
    }

    @Test
    fun `the evening chip rolls to tomorrow once the evening has gone`() {
        assertEquals(
            listOf("In 1 hour", "Tomorrow 7 pm", "Tomorrow 9 am"),
            labels(QuickTimeSettings(), "2026-08-19T20:00")
        )
    }

    @Test
    fun `the evening chip rolls at exactly the evening time, not after it`() {
        // 19:00 sharp is not "still ahead": setting it would produce a trigger
        // equal to now, which AlarmPlanner drops as already past.
        assertEquals("Tomorrow 7 pm", labels(QuickTimeSettings(), "2026-08-19T19:00")[1])
    }

    @Test
    fun `a custom evening time appears in the label with its minutes`() {
        val settings = QuickTimeSettings(eveningMinuteOfDay = 18 * 60 + 30)
        assertEquals("Tonight 6:30 pm", labels(settings, "2026-08-19T10:00")[1])
    }

    @Test
    fun `the relative chip names its own duration`() {
        assertEquals("In 30 minutes", labels(QuickTimeSettings(relativeMinutes = 30), "2026-08-19T10:00")[0])
        assertEquals("In 2 hours", labels(QuickTimeSettings(relativeMinutes = 120), "2026-08-19T10:00")[0])
        assertEquals("In 90 minutes", labels(QuickTimeSettings(relativeMinutes = 90), "2026-08-19T10:00")[0])
    }

    @Test
    fun `the relative chip is measured from now, to the minute`() {
        val presets = quickTimePresets(QuickTimeSettings(relativeMinutes = 60), at("2026-08-19T10:17:42"))
        // Seconds are trimmed so the chip lands on a whole minute rather than
        // 42 seconds past it.
        assertEquals(at("2026-08-19T11:17").toInstant().toEpochMilli(), presets[0].epochMillis)
    }

    @Test
    fun `the morning chip is always tomorrow, even first thing`() {
        val presets = quickTimePresets(QuickTimeSettings(), at("2026-08-19T06:00"))
        assertEquals(at("2026-08-20T09:00").toInstant().toEpochMilli(), presets[2].epochMillis)
        assertEquals("Tomorrow 9 am", presets[2].label)
    }

    @Test
    fun `every chip is in the future, whatever the hour`() {
        // The whole point of the rollover rules. A chip that sets a past
        // instant is never scheduled, so it silently does nothing.
        listOf("00:30", "08:59", "09:00", "18:59", "19:00", "23:59").forEach { time ->
            val now = at("2026-08-19T$time")
            quickTimePresets(QuickTimeSettings(), now).forEach { preset ->
                assert(preset.epochMillis > now.toInstant().toEpochMilli()) {
                    "${preset.label} is not in the future at $time"
                }
            }
        }
    }
}
