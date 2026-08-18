package com.stackpointer.lists.places

import com.stackpointer.lists.data.entity.ReminderEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The "only between" filter is the one part of the geofencing feature that can
 * be tested without a phone, and it is also the part most likely to be quietly
 * wrong: the platform can't schedule a geofence, so if this is broken the
 * reminder simply alerts at 3 am and nothing in the logs says why.
 */
class PlaceWindowTest {

    private val zone: ZoneId = ZoneId.of("Europe/Lisbon")

    private fun at(text: String): ZonedDateTime =
        LocalDateTime.parse(text).atZone(zone)

    private fun reminder(
        startMinute: Int? = null,
        endMinute: Int? = null,
        days: String? = null
    ) = ReminderEntity(
        id = 1,
        listId = 1,
        title = "Post the letter",
        createdAt = 0,
        placeId = 1,
        placeTrigger = "ARRIVE",
        placeWindowStartMinute = startMinute,
        placeWindowEndMinute = endMinute,
        placeWindowDays = days
    )

    @Test
    fun `no window always passes`() {
        assertTrue(isWithinPlaceWindow(reminder(), at("2026-08-18T03:00")))
    }

    @Test
    fun `a half-set window passes rather than blocking everything`() {
        // Guards against the failure that would be hardest to notice: a window
        // with one end missing silencing the reminder forever.
        assertTrue(isWithinPlaceWindow(reminder(startMinute = 8 * 60), at("2026-08-18T03:00")))
        assertTrue(isWithinPlaceWindow(reminder(endMinute = 22 * 60), at("2026-08-18T03:00")))
    }

    @Test
    fun `inside a daytime window`() {
        val r = reminder(startMinute = 8 * 60, endMinute = 22 * 60)
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T08:00")))
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T13:37")))
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T22:00")))
    }

    @Test
    fun `outside a daytime window`() {
        val r = reminder(startMinute = 8 * 60, endMinute = 22 * 60)
        assertFalse(isWithinPlaceWindow(r, at("2026-08-18T07:59")))
        assertFalse(isWithinPlaceWindow(r, at("2026-08-18T22:01")))
        assertFalse(isWithinPlaceWindow(r, at("2026-08-18T03:00")))
    }

    @Test
    fun `a window that crosses midnight covers both sides of it`() {
        // 10 pm to 6 am. The naive "start..end" range is empty here, which
        // would silence the reminder around the clock.
        val r = reminder(startMinute = 22 * 60, endMinute = 6 * 60)
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T23:30")))
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T05:59")))
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T22:00")))
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T06:00")))
        assertFalse(isWithinPlaceWindow(r, at("2026-08-18T12:00")))
        assertFalse(isWithinPlaceWindow(r, at("2026-08-18T21:59")))
    }

    @Test
    fun `equal start and end covers the whole day, not one minute`() {
        // Read as a one-minute window this silenced the reminder for 1439
        // minutes out of every 1440, while the editor said it ran overnight.
        val r = reminder(startMinute = 9 * 60, endMinute = 9 * 60)
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T09:00")))
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T09:01")))
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T03:00")))
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T21:30")))
    }

    @Test
    fun `a one-minute-apart window is still honoured as a real range`() {
        val r = reminder(startMinute = 9 * 60, endMinute = 9 * 60 + 1)
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T09:00")))
        assertTrue(isWithinPlaceWindow(r, at("2026-08-18T09:01")))
        assertFalse(isWithinPlaceWindow(r, at("2026-08-18T09:02")))
    }

    @Test
    fun `day filter keeps weekdays only`() {
        val r = reminder(days = "MO,TU,WE,TH,FR")
        // 2026-08-17 is a Monday; 2026-08-22 a Saturday.
        assertTrue(isWithinPlaceWindow(r, at("2026-08-17T09:00")))
        assertTrue(isWithinPlaceWindow(r, at("2026-08-21T09:00")))
        assertFalse(isWithinPlaceWindow(r, at("2026-08-22T09:00")))
        assertFalse(isWithinPlaceWindow(r, at("2026-08-23T09:00")))
    }

    @Test
    fun `day filter and hour window both have to pass`() {
        val r = reminder(startMinute = 9 * 60, endMinute = 17 * 60, days = "MO")
        assertTrue(isWithinPlaceWindow(r, at("2026-08-17T10:00")))
        assertFalse(isWithinPlaceWindow(r, at("2026-08-17T18:00")))
        assertFalse(isWithinPlaceWindow(r, at("2026-08-18T10:00")))
    }

    @Test
    fun `day codes are read case-insensitively and with stray spaces`() {
        val r = reminder(days = " mo , tu ")
        assertTrue(isWithinPlaceWindow(r, at("2026-08-17T10:00")))
        assertFalse(isWithinPlaceWindow(r, at("2026-08-19T10:00")))
    }

    @Test
    fun `an empty day list is treated as every day, not as no days`() {
        assertTrue(isWithinPlaceWindow(reminder(days = ""), at("2026-08-22T10:00")))
    }
}
