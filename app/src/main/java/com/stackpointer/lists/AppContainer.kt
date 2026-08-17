package com.stackpointer.lists

import android.content.Context
import com.stackpointer.lists.data.db.ListsDatabase
import com.stackpointer.lists.data.prefs.SearchHistoryStore
import com.stackpointer.lists.data.repository.ChecklistRepository
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.data.repository.ReminderRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = ListsDatabase.get(appContext)

    val reminderRepository = ReminderRepository(database.reminderDao())
    val listRepository = ListRepository(database.reminderListDao())
    val checklistRepository = ChecklistRepository(database.checklistItemDao())
    val searchHistoryStore = SearchHistoryStore(appContext)
}
