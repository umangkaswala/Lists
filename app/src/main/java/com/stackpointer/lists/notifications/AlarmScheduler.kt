package com.stackpointer.lists.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.getSystemService
import com.stackpointer.lists.data.dao.ReminderDao
import com.stackpointer.lists.data.prefs.SettingsStore
import com.stackpointer.lists.data.prefs.allDayAlertTime
import com.stackpointer.lists.data.repository.ReminderAlarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Keeps the OS's registered alarms in step with the reminders table.
 *
 * The only public write is [requestSync]: rather than trying to schedule and
 * unschedule individual reminders at each call site, every change triggers a
 * full re-sync. That's a few more binder calls, but it makes the whole thing
 * idempotent and kills an entire family of bugs at once — the stale alarm left
 * behind by an edit, the orphan after a cascading list delete, the double
 * alarm after an undo, the missing alarm after a restore.
 */
class AlarmScheduler(
    context: Context,
    private val reminderDao: ReminderDao,
    private val settingsStore: SettingsStore,
    scope: CoroutineScope
) : ReminderAlarms {

    private val appContext = context.applicationContext

    // CONFLATED: a burst of writes (undo restores several fields in a row)
    // collapses into a single re-sync instead of one per write.
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)

    /**
     * A sync cancels every alarm and then re-registers the survivors, so two
     * overlapping passes can interleave into a state matching neither — an
     * older plan's cancel wiping an alarm the newer plan had just written. The
     * boot worker and the alarm receiver both call [syncAll] directly, so
     * serialising here rather than at those call sites is what makes it safe.
     */
    private val syncLock = Mutex()

    init {
        scope.launch {
            for (ignored in syncRequests) {
                runCatching { syncAll() }
                    .onFailure { Log.e(TAG, "Alarm sync failed", it) }
            }
        }
    }

    override fun requestSync() {
        syncRequests.trySend(Unit)
    }

    suspend fun syncAll() = syncLock.withLock {
        val alarmManager = appContext.getSystemService<AlarmManager>() ?: return@withLock
        val reminders = reminderDao.getAllForScheduling()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        // Settings S16's "All-day reminders arrive at". Read per sync rather
        // than cached, so changing it in Settings takes effect on the very next
        // sync instead of the next process start.
        val allDayAlertTime = settingsStore.settings.first().allDayAlertTime

        // Read the permission once per sync rather than once per alarm: it's a
        // binder round-trip, and a sync runs on every onResume.
        val exact = canScheduleExact()

        // Cancel first, unconditionally, for every row we know about — cheaper
        // and far more reliable than tracking which ids currently have an alarm.
        // A row that was hard-deleted (a cascading list delete does that) can
        // leak one alarm; the receiver treats a missing row as a no-op, so the
        // worst case is a single wasted wake-up that never recurs.
        reminders.forEach { cancel(alarmManager, it.id) }
        cancel(alarmManager, HORIZON_ID)

        // A reminder that no longer deserves an alert shouldn't still be sitting
        // in the notification shade with live Done and Snooze buttons on it.
        // Completing, snoozing, deleting or re-timing one all end up here, which
        // saves every one of those call sites from having to remember.
        reminders.forEach { reminder ->
            val triggerAt = AlarmPlanner.triggerAtFor(reminder, zone, allDayAlertTime)
            if (triggerAt == null || triggerAt > now) {
                ReminderNotifier.cancel(appContext, reminder.id)
            }
        }

        val plan = AlarmPlanner.compute(
            reminders = reminders,
            nowMillis = now,
            zone = zone,
            allDayAlertTime = allDayAlertTime
        )

        plan.alarms.forEach { schedule(alarmManager, it.reminderId, it.triggerAtMillis, exact) }
        plan.horizonAt?.let { schedule(alarmManager, HORIZON_ID, it, exact) }
    }

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
        } else {
            true
        }

    private fun schedule(
        alarmManager: AlarmManager,
        id: Long,
        triggerAtMillis: Long,
        exact: Boolean
    ) {
        // FLAG_UPDATE_CURRENT always returns a PendingIntent, creating one if
        // needed — unlike the FLAG_NO_CREATE lookup used by cancel().
        val pendingIntent = checkNotNull(alarmPendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT))
        try {
            if (exact) {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                // Still gets through Doze, just not to the minute. Better a
                // reminder a few minutes late than one that never arrives.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // The exact-alarm permission can be revoked between the check above
            // and this call, and the OS throws rather than degrading. Documented
            // behaviour, not paranoia.
            Log.w(TAG, "Exact alarm denied for $id, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: IllegalStateException) {
            // The 500-alarms-per-app ceiling. AlarmPlanner caps well below it,
            // so this means something else in the app is leaking alarms.
            Log.e(TAG, "Alarm limit reached while scheduling $id", e)
        }
    }

    private fun cancel(alarmManager: AlarmManager, id: Long) {
        val existing = alarmPendingIntent(id, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(existing)
        existing.cancel()
    }

    /**
     * PendingIntents are matched on action, data, type, component and
     * categories — **extras are explicitly ignored**. Two reminders sharing a
     * request code and a bare intent would therefore be treated as the same
     * alarm, and each new reminder would silently replace the previous one:
     * the classic "only my most recent reminder ever fires" bug. The per-id
     * `data` URI is what actually keeps them distinct; the request code and
     * the extra are belt and braces.
     */
    private fun alarmPendingIntent(id: Long, extraFlags: Int): PendingIntent? {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_DUE
            data = Uri.parse("lists://reminder/$id")
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, id)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode(id),
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(id: Long): Int = (id % Int.MAX_VALUE).toInt()

    companion object {
        private const val TAG = "AlarmScheduler"
        const val ACTION_REMINDER_DUE = "com.stackpointer.lists.REMINDER_DUE"

        /**
         * Pseudo-reminder id for the "there were more alarms than we could
         * register, come back and plan again" alarm. Negative so it can never
         * collide with a Room row id.
         */
        const val HORIZON_ID = -1L
    }
}
