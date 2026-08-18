package com.stackpointer.lists.places

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.stackpointer.lists.data.dao.PlaceDao
import com.stackpointer.lists.data.entity.PlaceEntity
import com.stackpointer.lists.data.entity.ReminderEntity
import com.stackpointer.lists.data.repository.ReminderAlarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** What the last registration attempt did, so the Places screen can say so. */
data class GeofenceStatus(
    val registered: Int = 0,
    val requested: Int = 0,
    val missingPermission: Boolean = false,
    val lastError: String? = null
) {
    /** Design S05: warn at 90, the platform hard-stops at 100. */
    val isNearLimit: Boolean get() = requested >= WARN_AT
    val isOverLimit: Boolean get() = requested > MAX_GEOFENCES
}

/**
 * Google enforces 100 geofences per app per device; the 101st registration
 * fails the whole batch, not just the extra one, so the list is truncated
 * before it is ever sent.
 */
const val MAX_GEOFENCES = 100
const val WARN_AT = 90

/**
 * Keeps the device's registered geofences in step with the database.
 *
 * Modelled on [com.stackpointer.lists.notifications.AlarmScheduler]: callers
 * only ever say "something changed", and a single serialised worker re-reads
 * the database and re-registers everything. Geofences are lost on reboot, when
 * location services are toggled, and when the app is force-stopped, so a
 * full-resync-from-truth is the only thing that stays correct — there is no
 * incremental state worth trusting.
 */
class GeofenceRegistrar(
    private val context: Context,
    private val placeDao: PlaceDao,
    private val scope: CoroutineScope
) : ReminderAlarms {
    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    private val _status = MutableStateFlow(GeofenceStatus())
    val status: StateFlow<GeofenceStatus> = _status

    // Capacity 1 + DROP_OLDEST: several edits in a row only need one resync,
    // and the last one is the one that reflects the final state.
    private val requests = Channel<Unit>(capacity = Channel.CONFLATED)

    /** Request ids believed to be live on the device, for incremental syncs. */
    private var registeredIds: Set<String> = emptySet()
    private var didInitialClear = false

    init {
        scope.launch {
            for (ignored in requests) {
                runCatching { sync() }
                    .onFailure { Log.e(TAG, "Geofence sync failed", it) }
            }
        }
    }

    override fun requestSync() {
        requests.trySend(Unit)
    }

    /**
     * Forces the next sync to re-register everything from scratch, for when
     * the platform tells us the fences are gone (GEOFENCE_NOT_AVAILABLE) rather
     * than us changing them ourselves.
     */
    fun requestFullRebuild() {
        registeredIds = emptySet()
        didInitialClear = false
        requestSync()
    }

    private suspend fun sync() {
        if (!hasLocationPermission(context)) {
            // Nothing to remove either: without the permission the remove call
            // throws too, and the fences are already inert.
            registeredIds = emptySet()
            _status.value = GeofenceStatus(
                requested = placeDao.activePlaceReminders().size,
                missingPermission = true
            )
            return
        }

        val reminders = placeDao.activePlaceReminders()
        val places = placeDao.getAll().associateBy { it.id }
        val fences = reminders.mapNotNull { reminder ->
            places[reminder.placeId]?.let { place -> buildGeofence(reminder, place) }
        }

        // Once per process, wipe whatever a previous process left registered:
        // fences outlive the app, so a reminder deleted while the app was dead
        // would otherwise keep its fence forever.
        //
        // After that the sync is incremental. A blanket remove-then-add on
        // every call left a window — milliseconds, but real — where *no*
        // fences existed, and a crossing during it is lost for good, because
        // setInitialTrigger(0) means re-registering can't notice it after the
        // fact. requestSync() runs on every reminder edit and every
        // PROVIDERS_CHANGED, so that window came round often.
        if (!didInitialClear) {
            runCatching { client.removeGeofences(pendingIntent()).await() }
                .onFailure { Log.w(TAG, "Could not clear geofences", it) }
            didInitialClear = true
            registeredIds = emptySet()
        }

        val wantedIds = fences.map { it.requestId }.toSet()
        val goneIds = (registeredIds - wantedIds).toList()
        if (goneIds.isNotEmpty()) {
            runCatching { client.removeGeofences(goneIds).await() }
                .onFailure { Log.w(TAG, "Could not remove stale geofences", it) }
        }

        if (fences.isEmpty()) {
            registeredIds = emptySet()
            _status.value = GeofenceStatus(registered = 0, requested = 0)
            return
        }

        // activePlaceReminders() comes back newest-first, so this keeps the
        // newest MAX_GEOFENCES and drops the oldest — which is what the Places
        // screen's warning tells the user will happen. Without the ORDER BY the
        // set was whatever SQLite felt like and could differ between syncs.
        val kept = fences.take(MAX_GEOFENCES)
        val request = GeofencingRequest.Builder()
            // No INITIAL_TRIGGER: registering an "arrive at home" fence while
            // already at home would otherwise alert instantly, every time the
            // list is re-synced. Only a real crossing should count.
            .setInitialTrigger(0)
            .addGeofences(kept)
            .build()

        val error = runCatching { client.addGeofences(request, pendingIntent()).await() }
            .exceptionOrNull()

        // addGeofences replaces by request id, so a fence whose definition
        // hasn't changed is never actually off the device.
        registeredIds = if (error == null) kept.map { it.requestId }.toSet() else emptySet()
        _status.value = GeofenceStatus(
            registered = if (error == null) kept.size else 0,
            requested = fences.size,
            lastError = error?.let { describe(it) }
        )
        if (error != null) Log.e(TAG, "Could not register geofences", error)
    }

    private fun buildGeofence(reminder: ReminderEntity, place: PlaceEntity): Geofence? {
        val trigger = PlaceTrigger.parse(reminder.placeTrigger) ?: return null
        val transition = when (trigger) {
            PlaceTrigger.ARRIVE -> Geofence.GEOFENCE_TRANSITION_ENTER
            PlaceTrigger.LEAVE -> Geofence.GEOFENCE_TRANSITION_EXIT
        }
        return Geofence.Builder()
            // The reminder id, not the place id: two reminders on the same
            // place with opposite triggers are two different fences, and the
            // receiver has to know which reminder to alert about.
            .setRequestId(requestIdFor(reminder.id))
            .setCircularRegion(place.latitude, place.longitude, place.radiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transition)
            .build()
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
            .setAction(GeofenceReceiver.ACTION_GEOFENCE_EVENT)
        // MUTABLE is required, not a preference: Play Services fills the
        // triggering-geofence extras into this intent, and an immutable one
        // would be delivered empty on Android 12+.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun describe(error: Throwable): String = when {
        error.message?.contains("1000") == true ->
            "Geofencing is unavailable — check that location is switched on."
        error.message?.contains("1001") == true ->
            "Too many place reminders for this device to track."
        else -> error.message ?: "Could not register place reminders."
    }

    companion object {
        private const val TAG = "GeofenceRegistrar"
        private const val REQUEST_CODE = 90210
        private const val ID_PREFIX = "reminder-"

        fun requestIdFor(reminderId: Long): String = "$ID_PREFIX$reminderId"

        fun reminderIdFrom(requestId: String): Long? =
            requestId.removePrefix(ID_PREFIX).toLongOrNull()
    }
}

/**
 * Geofencing needs *precise* location. Android 12 lets the user grant
 * approximate only, which looks like a granted permission but cannot drive a
 * geofence, so COARSE is deliberately not accepted here.
 */
fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!fine) return false
    // Below Android 10 there is no separate background permission; foreground
    // access covers a geofence firing while the app is closed.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}
