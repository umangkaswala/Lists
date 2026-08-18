package com.stackpointer.lists.places

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stackpointer.lists.ListsApplication

/**
 * Re-registers geofences after the user turns location off and on again.
 *
 * Play Services silently drops every registered geofence when location is
 * disabled and does not put them back when it returns. Nothing else in the
 * system tells the app about it, so without this a single trip through the
 * quick-settings tile would leave every place reminder permanently dead — the
 * worst kind of failure, because the app would still list them as active.
 *
 * Exported because these are system broadcasts from another UID.
 */
class LocationSettingsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ListsApplication ?: return
        // Cheap and idempotent: the registrar reads the database and rebuilds
        // the whole set, so a spurious broadcast costs nothing.
        app.container.geofenceRegistrar.requestSync()
    }
}
