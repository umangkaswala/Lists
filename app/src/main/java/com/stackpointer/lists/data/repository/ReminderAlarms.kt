package com.stackpointer.lists.data.repository

/**
 * How a repository asks for OS-side state — exact alarms, and from Phase 7
 * geofences too — to be brought back in line with the database. Android-free on purpose, so the repositories stay unit-testable
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

/**
 * Fans one "the database changed" signal out to everything that mirrors it into
 * OS state — exact alarms and geofences both.
 *
 * They are invalidated by exactly the same edits (completing, snoozing,
 * binning, restoring, editing a reminder), so threading a second dependency
 * through every repository method would be a dozen call sites that must never
 * disagree with each other. One signal, two listeners.
 */
class ReminderSyncFanOut(private val targets: List<ReminderAlarms>) : ReminderAlarms {
    override fun requestSync() {
        targets.forEach { it.requestSync() }
    }
}
