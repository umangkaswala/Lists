package com.stackpointer.lists.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.stackpointer.lists.ListsApplication
import kotlinx.coroutines.launch

/**
 * Receives a fired reminder alarm and posts its notification.
 *
 * The notification is posted from here directly rather than being handed to
 * WorkManager: enqueue-to-execute latency is seconds at best and unbounded
 * under constraints, which for the fire path is a visibly late reminder for no
 * benefit. WorkManager's place in this phase is the boot re-sync, where a few
 * seconds cost nothing.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, MISSING_ID)
        val app = context.applicationContext as? ListsApplication ?: return
        val container = app.container

        // onReceive runs on the main thread and Room refuses main-thread
        // queries, so the work has to move off it — and goAsync() is what keeps
        // the process (and AlarmManager's wakelock) alive until it's finished.
        // pendingResult.finish() must run on every path or the device stays awake.
        val pendingResult = goAsync()
        container.applicationScope.launch {
            try {
                if (reminderId == AlarmScheduler.HORIZON_ID) {
                    // Not a reminder: the "there were more alarms than would
                    // fit" marker. Its only job is to trigger a re-plan.
                    container.alarmScheduler.syncAll()
                    return@launch
                }
                if (reminderId <= 0L) return@launch

                container.reminderAlerts.notifyDue(reminderId)
                // The alarm that just fired is spent; re-sync so this reminder's
                // slot is released and anything waiting behind the cap moves up.
                container.alarmScheduler.syncAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle alarm for $reminderId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderAlarmReceiver"
        private const val MISSING_ID = 0L
        const val EXTRA_REMINDER_ID = "reminderId"
    }
}
