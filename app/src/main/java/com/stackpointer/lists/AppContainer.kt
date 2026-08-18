package com.stackpointer.lists

import android.content.Context
import com.stackpointer.lists.data.db.ListsDatabase
import com.stackpointer.lists.data.prefs.OnboardingStore
import com.stackpointer.lists.data.prefs.SearchHistoryStore
import com.stackpointer.lists.data.repository.ChecklistRepository
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.data.repository.PlaceRepository
import com.stackpointer.lists.data.repository.ReminderRepository
import com.stackpointer.lists.data.repository.ReminderSyncFanOut
import com.stackpointer.lists.notifications.AlarmScheduler
import com.stackpointer.lists.notifications.ReminderAlerts
import com.stackpointer.lists.places.GeofenceRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = ListsDatabase.get(appContext)

    /**
     * Lives as long as the process. Work that must outlive the screen that
     * started it goes here — alarm scheduling above all, since a reminder saved
     * from the Capture sheet has to get its alarm even though the sheet's own
     * scope dies the instant it closes.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val reminderDao = database.reminderDao()
    val completionDao = database.completionDao()
    val placeDao = database.placeDao()
    val listDao = database.reminderListDao()

    val alarmScheduler = AlarmScheduler(appContext, reminderDao, applicationScope)

    val reminderAlerts = ReminderAlerts(appContext, reminderDao, listDao)

    val geofenceRegistrar = GeofenceRegistrar(appContext, placeDao, applicationScope)

    /**
     * Every reminder edit invalidates both the alarm schedule and the geofence
     * registrations, so the repositories are handed one signal that reaches
     * both rather than two they could forget to call in step.
     */
    private val osStateSync = ReminderSyncFanOut(listOf(alarmScheduler, geofenceRegistrar))

    val reminderRepository = ReminderRepository(reminderDao, completionDao, osStateSync)
    val listRepository = ListRepository(listDao, osStateSync)
    val placeRepository = PlaceRepository(placeDao, geofenceRegistrar)
    val checklistRepository = ChecklistRepository(database.checklistItemDao())
    val searchHistoryStore = SearchHistoryStore(appContext)
    val onboardingStore = OnboardingStore(appContext)
}
