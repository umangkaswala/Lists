package com.stackpointer.lists.onboarding

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.stackpointer.lists.notifications.NotificationChannels

/**
 * Live, on-demand reads of the three permissions the onboarding screen primes.
 *
 * Nothing here is cached: every value is re-read from the OS on request,
 * because all three can be revoked from system Settings while the app is
 * running, and the exact-alarm one is granted *outside* our process entirely
 * (there is no result callback to observe).
 */
object PermissionState {

    /**
     * On API 33+ this is a real runtime permission. Below that, notifications
     * are granted at install time, so the only way to be "denied" is the user
     * switching them off in system Settings — which [canDeliverAlerts]
     * covers for both cases.
     */
    fun hasNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /**
     * Exact alarms. On API 31/32 SCHEDULE_EXACT_ALARM is granted at install;
     * from API 33 it defaults to *denied* for apps that only declare that
     * permission, and can only be turned on by the user in system Settings.
     * Below API 31 exact alarms need no permission at all.
     */
    fun hasExactAlarms(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
        } else {
            true
        }

    /**
     * Whether a posted reminder would actually reach the user.
     *
     * Broader than [hasNotifications] on purpose: the permission can be granted
     * while notifications are switched off app-wide, or while the Reminders
     * channel itself has been muted to IMPORTANCE_NONE. In every one of those
     * cases `notify()` succeeds and shows nothing at all — no exception, no log
     * — so the app has to notice and say so, or it just looks broken.
     */
    fun canDeliverAlerts(context: Context): Boolean {
        if (!hasNotifications(context)) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = context.getSystemService<NotificationManager>()
            ?.getNotificationChannel(NotificationChannels.REMINDERS)
        // A channel that doesn't exist yet is fine — it's created on first use.
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Opens this app's notification settings, where the switch actually lives. */
    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /**
     * Precise foreground location. Coarse is deliberately not accepted: from
     * Android 12 the user can grant "Approximate" instead, which reads as a
     * granted location permission but cannot drive a geofence.
     */
    fun hasLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** True when the user granted "Approximate" only — precise is still missing. */
    fun hasApproximateLocationOnly(context: Context): Boolean {
        if (hasLocation(context)) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * "Allow all the time". Needed for a place reminder to fire while the app
     * is closed, which is every time that matters. Below Android 10 there is no
     * separate background permission and foreground access covers it.
     */
    fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Whether background location can still be asked for with a dialog.
     *
     * From Android 11 it cannot: the system refuses to show one, and a runtime
     * request is denied on the spot without the user ever seeing anything. The
     * only route is [appSettingsIntent] and asking the person to pick "Allow
     * all the time" themselves — which is what design S05's "Fix" button does.
     */
    fun canRequestBackgroundLocationInApp(): Boolean =
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q

    /**
     * Exact-alarm access can't be requested with a permission dialog — it's a
     * per-app toggle in system Settings, reached with this intent. Returns null
     * below API 31, where there's nothing to ask for.
     */
    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.fromParts("package", context.packageName, null)
            )
        } else {
            null
        }

    /** Falls back to the app's own settings page, for a second-denial case. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
}
