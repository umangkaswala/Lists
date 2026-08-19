package com.stackpointer.lists.notifications

import com.stackpointer.lists.data.entity.ReminderEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** One reminder's alarm: which row, and the exact instant it should fire. */
data class ScheduledAlarm(val reminderId: Long, val triggerAtMillis: Long)

/**
 * The complete set of alarms that *should* currently be registered.
 *
 * [horizonAt] is set only when [AlarmPlan.compute] had to truncate: it's the
 * instant at which a plain "re-plan now" alarm should fire so the reminders
 * that didn't fit get scheduled once the earlier ones have passed.
 */
data class AlarmPlan(
    val alarms: List<ScheduledAlarm>,
    val horizonAt: Long? = null
)

/**
 * Decides which reminders get an alarm and when — deliberately pure Kotlin
 * with no Android imports, so every rule below is unit-testable without a
 * device (the same approach that made the RRULE engine in Phase 3 provable).
 *
 * A repeating reminder is *one* alarm, not a fan-out of occurrences:
 * `ReminderRepository.setCompleted` moves `dueAt` forward on completion, so
 * one row always has exactly one next occurrence.
 */
object AlarmPlanner {

    /**
     * The OS hard limit is 500 concurrent alarms per app, and going over it
     * throws. Staying well under leaves room for Phase 7's geofences and the
     * widget refreshes in Phases 10/11.
     */
    const val MAX_ALARMS = 200

    /**
     * An all-day reminder stores whatever instant it was created at, which is
     * meaningless as an alert time — notifying at 00:00 would wake people up.
     * 09:00 local is the default; Settings S16's "All-day reminders arrive at"
     * row overrides it, which is why every function here takes it as a
     * parameter rather than reading a constant.
     */
    val DEFAULT_ALL_DAY_ALERT_TIME: LocalTime = LocalTime.of(9, 0)

    /**
     * How long after its due time a reminder is still worth alerting about
     * when the device was off or the app force-stopped. Used by the boot
     * resync, not by ordinary scheduling.
     */
    const val CATCH_UP_WINDOW_MILLIS = 60L * 60L * 1000L

    fun compute(
        reminders: List<ReminderEntity>,
        nowMillis: Long,
        zone: ZoneId,
        max: Int = MAX_ALARMS,
        allDayAlertTime: LocalTime = DEFAULT_ALL_DAY_ALERT_TIME
    ): AlarmPlan {
        val due = reminders
            .mapNotNull { reminder ->
                triggerAtFor(reminder, zone, allDayAlertTime)?.let { ScheduledAlarm(reminder.id, it) }
            }
            // Past occurrences are never scheduled: AlarmManager would fire
            // them immediately, so every app launch would replay old alerts.
            .filter { it.triggerAtMillis > nowMillis }
            .sortedBy { it.triggerAtMillis }

        if (due.size <= max) return AlarmPlan(alarms = due)

        val kept = due.take(max)
        return AlarmPlan(
            alarms = kept,
            // One extra alarm just after the last one we could fit, whose only
            // job is to re-run planning. Without it, reminder 201 onwards would
            // never be scheduled at all.
            horizonAt = kept.last().triggerAtMillis + 60_000L
        )
    }

    /**
     * Reminders that fell due while the device was off (or the app was
     * force-stopped, which stops alarms entirely until the next launch) and
     * are still recent enough to be worth mentioning.
     */
    fun missedSince(
        reminders: List<ReminderEntity>,
        nowMillis: Long,
        zone: ZoneId,
        windowMillis: Long = CATCH_UP_WINDOW_MILLIS,
        allDayAlertTime: LocalTime = DEFAULT_ALL_DAY_ALERT_TIME
    ): List<ScheduledAlarm> = reminders
        .mapNotNull { reminder ->
            triggerAtFor(reminder, zone, allDayAlertTime)?.let { ScheduledAlarm(reminder.id, it) }
        }
        .filter { it.triggerAtMillis in (nowMillis - windowMillis)..nowMillis }
        .sortedBy { it.triggerAtMillis }

    /** Null when this reminder should never alert at all. */
    fun triggerAtFor(
        reminder: ReminderEntity,
        zone: ZoneId,
        allDayAlertTime: LocalTime = DEFAULT_ALL_DAY_ALERT_TIME
    ): Long? {
        if (reminder.isCompleted) return null
        if (reminder.deletedAt != null) return null
        val dueAt = reminder.dueAt ?: return null

        return if (reminder.isAllDay) {
            Instant.ofEpochMilli(dueAt)
                .atZone(zone)
                .toLocalDate()
                .atTime(allDayAlertTime)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        } else {
            dueAt
        }
    }
}
