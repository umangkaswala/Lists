package com.stackpointer.lists

import android.content.Context
import com.stackpointer.lists.data.db.ListsDatabase
import com.stackpointer.lists.data.repository.ListRepository
import com.stackpointer.lists.data.repository.ReminderRepository

class AppContainer(context: Context) {
    private val database = ListsDatabase.get(context)

    val reminderRepository = ReminderRepository(database.reminderDao())
    val listRepository = ListRepository(database.reminderListDao())
}
