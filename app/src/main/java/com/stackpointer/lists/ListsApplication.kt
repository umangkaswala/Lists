package com.stackpointer.lists

import android.app.Application
import com.stackpointer.lists.notifications.NotificationChannels
import kotlinx.coroutines.launch

class ListsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        NotificationChannels.ensure(this)

        // Every process start re-syncs. It's idempotent and cheap, and it's the
        // backstop for the cases nothing else covers — a force-stopped app gets
        // no broadcasts at all until the user next opens it, so boot handling
        // alone would leave its alarms gone.
        container.alarmScheduler.requestSync()

        // Same reasoning for geofences, and one more case besides: a
        // force-stopped app loses its registered fences outright, and only
        // being opened again can put them back.
        container.geofenceRegistrar.requestSync()

        // Recycle-bin retention. Opening the app is a good enough trigger: the
        // bin is only ever *seen* from inside the app, so a reminder can't look
        // overdue for deletion before this has had a chance to run. A
        // WorkManager job would buy nothing but a second thing to get wrong.
        container.applicationScope.launch {
            container.reminderRepository.purgeExpiredBin()
        }
    }
}
