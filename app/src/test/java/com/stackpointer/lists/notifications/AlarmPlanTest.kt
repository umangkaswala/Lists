package com.stackpointer.lists.notifications

import com.stackpointer.lists.data.entity.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmPlanTest {

    private val zone: ZoneId = ZoneId.of("Europe/Lisbon")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun reminder(
        id: Long,
        dueAt: Long?,
        isAllDay: Boolean = false,
        isCompleted: Boolean = false,
        deletedAt: Long? = null
    ) = ReminderEntity(
        id = id,
        listId = 1L,
        title = "Reminder $id",
        dueAt = dueAt,
        isAllDay = isAllDay,
        isCompleted = isCompleted,
        deletedAt = deletedAt,
        createdAt = 0L
    )

    @Test
    fun `schedules a future timed reminder at its due time`() {
        val due = at("2026-08-20T19:00")
        val plan = AlarmPlanner.compute(
            reminders = listOf(reminder(1, due)),
            nowMillis = at("2026-08-20T18:00"),
            zone = zone
        )

        assertEquals(listOf(ScheduledAlarm(1L, due)), plan.alarms)
        assertNull(plan.horizonAt)
    }

    @Test
    fun `an all-day reminder alerts at 9am, not at the instant it was created`() {
        // Stored at 14:30 on the due date, as an all-day reminder created that
        // afternoon would be.
        val plan = AlarmPlanner.compute(
            reminders = listOf(reminder(1, at("2026-08-25T14:30"), isAllDay = true)),
            nowMillis = at("2026-08-20T09:00"),
            zone = zone
        )

        assertEquals(listOf(ScheduledAlarm(1L, at("2026-08-25T09:00"))), plan.alarms)
    }

    @Test
    fun `past due times are never scheduled`() {
        val plan = AlarmPlanner.compute(
            reminders = listOf(reminder(1, at("2026-08-20T08:00"))),
            nowMillis = at("2026-08-20T09:00"),
            zone = zone
        )

        assertTrue(plan.alarms.isEmpty())
    }

    @Test
    fun `completed, soft-deleted and undated reminders get no alarm`() {
        val future = at("2026-08-21T10:00")
        val plan = AlarmPlanner.compute(
            reminders = listOf(
                reminder(1, future, isCompleted = true),
                reminder(2, future, deletedAt = at("2026-08-20T09:00")),
                reminder(3, dueAt = null)
            ),
            nowMillis = at("2026-08-20T09:00"),
            zone = zone
        )

        assertTrue(plan.alarms.isEmpty())
    }

    @Test
    fun `alarms come back in time order`() {
        val plan = AlarmPlanner.compute(
            reminders = listOf(
                reminder(1, at("2026-08-22T10:00")),
                reminder(2, at("2026-08-20T10:00")),
                reminder(3, at("2026-08-21T10:00"))
            ),
            nowMillis = at("2026-08-20T09:00"),
            zone = zone
        )

        assertEquals(listOf(2L, 3L, 1L), plan.alarms.map { it.reminderId })
    }

    @Test
    fun `over the cap, the soonest are kept and a horizon alarm is added`() {
        val now = at("2026-08-20T09:00")
        val reminders = (1L..5L).map { i ->
            reminder(i, now + i * 60_000L)
        }

        val plan = AlarmPlanner.compute(reminders, nowMillis = now, zone = zone, max = 3)

        assertEquals(listOf(1L, 2L, 3L), plan.alarms.map { it.reminderId })
        // Just after the last one that fit, so planning re-runs and picks up 4 and 5.
        assertEquals(now + 3 * 60_000L + 60_000L, plan.horizonAt)
    }

    @Test
    fun `a repeating reminder is one alarm, not an expansion of its series`() {
        val due = at("2026-08-25T19:00")
        val repeating = reminder(1, due).copy(
            repeatRule = "FREQ=WEEKLY;BYDAY=TU",
            seriesStartAt = at("2026-08-18T19:00")
        )

        val plan = AlarmPlanner.compute(
            reminders = listOf(repeating),
            nowMillis = at("2026-08-20T09:00"),
            zone = zone
        )

        assertEquals(1, plan.alarms.size)
        assertEquals(due, plan.alarms.single().triggerAtMillis)
    }

    @Test
    fun `missedSince finds recent overdue reminders but not ancient ones`() {
        val now = at("2026-08-20T09:00")
        val plan = AlarmPlanner.missedSince(
            reminders = listOf(
                reminder(1, now - 10 * 60_000L),        // 10 minutes ago
                reminder(2, now - 5 * 60 * 60_000L),    // 5 hours ago
                reminder(3, now + 10 * 60_000L)         // still to come
            ),
            nowMillis = now,
            zone = zone
        )

        assertEquals(listOf(1L), plan.map { it.reminderId })
    }

    @Test
    fun `an all-day reminder due today is skipped once 9am has passed`() {
        val plan = AlarmPlanner.compute(
            reminders = listOf(reminder(1, at("2026-08-20T00:00"), isAllDay = true)),
            nowMillis = at("2026-08-20T10:00"),
            zone = zone
        )

        assertTrue(plan.alarms.isEmpty())
    }

    @Test
    fun `all-day alerts stay at 9am local across the spring DST change`() {
        // Europe/Lisbon springs forward on 29 March 2026 at 01:00.
        val plan = AlarmPlanner.compute(
            reminders = listOf(reminder(1, at("2026-03-30T12:00"), isAllDay = true)),
            nowMillis = at("2026-03-28T09:00"),
            zone = zone
        )

        val triggerLocalTime = java.time.Instant
            .ofEpochMilli(plan.alarms.single().triggerAtMillis)
            .atZone(zone)
            .toLocalTime()

        assertEquals(java.time.LocalTime.of(9, 0), triggerLocalTime)
    }
}
