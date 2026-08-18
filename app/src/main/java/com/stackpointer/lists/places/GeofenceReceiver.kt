package com.stackpointer.lists.places

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.stackpointer.lists.ListsApplication
import com.stackpointer.lists.data.entity.PlaceEntity
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.notifications.ReminderNotifier
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Turns a geofence crossing into a notification.
 *
 * Registered as `exported="false"`: it is only ever reached through the
 * registrar's own PendingIntent, never as a system broadcast.
 */
class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GEOFENCE_EVENT) return

        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) return
        if (event.hasError()) {
            Log.w(TAG, "Geofence event error: ${event.errorCode}")
            // GEOFENCE_NOT_AVAILABLE means the platform has dropped our
            // registrations — usually because location was switched off, or
            // NLP was restarted. Logging and walking away leaves every place
            // reminder dead until the app is next opened, which is the exact
            // failure LocationSettingsReceiver exists to prevent; that receiver
            // just doesn't hear about this route.
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                (context.applicationContext as? ListsApplication)
                    ?.container
                    ?.geofenceRegistrar
                    ?.requestFullRebuild()
            }
            return
        }

        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            transition != Geofence.GEOFENCE_TRANSITION_EXIT
        ) {
            return
        }

        val reminderIds = event.triggeringGeofences
            ?.mapNotNull { GeofenceRegistrar.reminderIdFrom(it.requestId) }
            ?.distinct()
            .orEmpty()
        if (reminderIds.isEmpty()) return

        val app = context.applicationContext as? ListsApplication ?: return
        val container = app.container

        // onReceive runs on the main thread and Room refuses main-thread
        // queries, so the work moves off it — and every path below has to reach
        // finish() or the device is held awake.
        val pendingResult = goAsync()
        container.applicationScope.launch {
            try {
                val zone = ZoneId.systemDefault()
                val now = ZonedDateTime.now(zone)
                reminderIds.forEach { id ->
                    val reminder = container.reminderDao.getById(id) ?: return@forEach
                    if (reminder.isCompleted || reminder.deletedAt != null) return@forEach

                    // Belt and braces: a fence is registered for one direction
                    // only, but a stale registration from a trigger the user
                    // has since flipped would otherwise alert the wrong way.
                    val expected = when (PlaceTrigger.parse(reminder.placeTrigger)) {
                        PlaceTrigger.ARRIVE -> Geofence.GEOFENCE_TRANSITION_ENTER
                        PlaceTrigger.LEAVE -> Geofence.GEOFENCE_TRANSITION_EXIT
                        null -> return@forEach
                    }
                    if (expected != transition) return@forEach

                    if (!isWithinPlaceWindow(reminder, now)) return@forEach

                    val place = reminder.placeId?.let { container.placeDao.getById(it) }
                    val listName = container.listDao.getById(reminder.listId)?.name
                    ReminderNotifier.show(
                        context = context,
                        reminder = reminder,
                        listName = listName,
                        overrideSubtitle = placeSubtitle(reminder, place, listName)
                    )
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Could not handle geofence event", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** The design's wording: "You just left Work". */
    private fun placeSubtitle(
        reminder: ReminderEntity,
        place: PlaceEntity?,
        listName: String?
    ): String {
        val placeName = place?.name ?: "the place you saved"
        val lead = when (PlaceTrigger.parse(reminder.placeTrigger)) {
            PlaceTrigger.LEAVE -> "You just left $placeName"
            else -> "You just arrived at $placeName"
        }
        return listOfNotNull(lead, listName).joinToString(" · ")
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.stackpointer.lists.GEOFENCE_EVENT"
        private const val TAG = "GeofenceReceiver"
    }
}
