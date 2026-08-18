package com.stackpointer.lists.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * Alarms don't survive a reboot — or an app update, or a clock change large
 * enough to make an absolute alarm meaningless. Each of those has to put the
 * whole schedule back.
 *
 * This receiver must be `android:exported="true"`: these are system broadcasts
 * sent from another UID, and an unexported receiver simply never fires — with
 * no error to explain why.
 *
 * Deliberately *not* `directBootAware`. It would then run before the device is
 * unlocked for the first time, where the credential-encrypted Room database
 * can't even be opened. The consequence is real and worth knowing: on a phone
 * with a PIN or fingerprint, reminders aren't rescheduled until the first
 * unlock after a reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val isBoot = intent.action == Intent.ACTION_BOOT_COMPLETED

        val request = OneTimeWorkRequestBuilder<RescheduleAlarmsWorker>()
            .setInputData(workDataOf(RescheduleAlarmsWorker.KEY_CATCH_UP to isBoot))
            .build()

        // The work is enqueued rather than done here: BOOT_COMPLETED is fanned
        // out to every app at once on a device that's still booting, and a
        // receiver gets roughly ten seconds with no retry if it's killed.
        //
        // The boot run gets its own unique-work name. Sharing one name with
        // REPLACE meant a TIME_SET or TIMEZONE_CHANGED arriving moments after
        // boot — which is exactly when a phone corrects its clock off the
        // network — cancelled the boot run, and with it the catch-up for every
        // reminder that came due while the phone was off.
        WorkManager.getInstance(context).enqueueUniqueWork(
            if (isBoot) WORK_NAME_BOOT else WORK_NAME_RESYNC,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private companion object {
        const val WORK_NAME_BOOT = "reschedule-alarms-boot"
        const val WORK_NAME_RESYNC = "reschedule-alarms"
    }
}
