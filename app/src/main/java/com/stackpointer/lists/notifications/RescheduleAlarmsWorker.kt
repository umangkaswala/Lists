package com.stackpointer.lists.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stackpointer.lists.ListsApplication
import com.stackpointer.lists.data.prefs.allDayAlertTime
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * Puts every alarm back after a reboot, an app update or a clock change, and —
 * after a reboot only — alerts on anything that fell due while the phone was
 * off, provided it's recent enough to still be worth saying.
 */
class RescheduleAlarmsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ListsApplication ?: return Result.success()
        val container = app.container

        return try {
            if (inputData.getBoolean(KEY_CATCH_UP, false)) {
                notifyMissed(container)
            }
            container.alarmScheduler.syncAll()
            // Geofences are wiped by a reboot exactly as alarms are, and this
            // worker is already the thing that runs once the device is
            // unlocked and the database is readable.
            container.geofenceRegistrar.requestSync()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Reminders whose time passed while the device was off. Only the last
     * hour's worth: waking up to a burst of notifications for everything missed
     * overnight would be worse than the red Overdue section already on Home.
     */
    private suspend fun notifyMissed(container: com.stackpointer.lists.AppContainer) {
        val reminders = container.reminderDao.getAllForScheduling()
        val missed = AlarmPlanner.missedSince(
            reminders = reminders,
            nowMillis = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
            allDayAlertTime = container.settingsStore.settings.first().allDayAlertTime
        )
        missed.forEach { container.reminderAlerts.notifyDue(it.reminderId) }
    }

    companion object {
        const val KEY_CATCH_UP = "catchUp"
    }
}
