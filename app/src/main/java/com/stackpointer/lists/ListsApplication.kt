package com.stackpointer.lists

import android.app.Application
import com.stackpointer.lists.notifications.NotificationChannels

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
    }
}
