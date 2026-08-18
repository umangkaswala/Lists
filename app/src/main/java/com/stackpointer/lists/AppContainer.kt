package com.stackpointer.lists

import android.content.Context
import com.stackpointer.lists.data.db.ListsDatabase
import com.stackpointer.lists.data.prefs.OnboardingStore
import com.stackpointer.lists.data.prefs.SearchHistoryStore
import com.stackpointer.lists.data.repository.ChecklistRepository
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.data.repository.ReminderRepository
import com.stackpointer.lists.notifications.AlarmScheduler
import com.stackpointer.lists.notifications.ReminderAlerts
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

    val alarmScheduler = AlarmScheduler(appContext, reminderDao, applicationScope)

    val reminderAlerts = ReminderAlerts(appContext, reminderDao, database.reminderListDao())

    val reminderRepository = ReminderRepository(reminderDao, alarmScheduler)
    val listRepository = ListRepository(database.reminderListDao(), alarmScheduler)
    val checklistRepository = ChecklistRepository(database.checklistItemDao())
    val searchHistoryStore = SearchHistoryStore(appContext)
    val onboardingStore = OnboardingStore(appContext)
}
