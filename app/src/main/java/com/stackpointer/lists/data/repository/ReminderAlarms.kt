package com.stackpointer.lists.data.repository

/**
 * How a repository asks for the OS alarms to be brought back in line with the
 * database. Android-free on purpose, so the repositories stay unit-testable
 * and so a test can pass [None].
 *
 * [requestSync] must be non-suspending and must do its work on an
 * application-scoped coroutine, **not** the caller's. CLAUDE.md records the
 * Phase 4 bug where the Capture sheet's scope died the moment the sheet
 * closed; the same shape here would commit the reminder to the database and
 * silently never schedule its alarm — which would present as "sometimes my
 * reminder just doesn't go off", about the hardest bug there is to chase.
 */
interface ReminderAlarms {
    fun requestSync()

    object None : ReminderAlarms {
        override fun requestSync() = Unit
    }
}
