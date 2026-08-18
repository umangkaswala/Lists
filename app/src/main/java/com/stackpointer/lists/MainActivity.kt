package com.stackpointer.lists

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stackpointer.lists.navigation.ListsNavHost
import com.stackpointer.lists.ui.theme.ListsTheme

class MainActivity : ComponentActivity() {

    // Set when the activity is opened by tapping a reminder notification, and
    // cleared once the NavHost has consumed it, so it doesn't re-navigate on
    // every recomposition or configuration change.
    private var pendingReminderId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingReminderId = intent.consumeReminderId()

        setContent {
            ListsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ListsNavHost(
                        pendingReminderId = pendingReminderId,
                        onPendingReminderHandled = { pendingReminderId = null }
                    )
                }
            }
        }
    }

    /**
     * With launchMode="singleTop" a notification tap on an already-running app
     * arrives here rather than in onCreate. Missing this half is why "the
     * notification opens the app but not the reminder" is such a common bug.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.consumeReminderId()?.let { pendingReminderId = it }
    }

    override fun onResume() {
        super.onResume()
        // Cheap and idempotent. Covers a re-grant of the exact-alarm permission
        // (which the OS grants outside our process, and which cancels every
        // existing alarm when it's revoked) and any alarm lost to a force-stop.
        (application as ListsApplication).container.alarmScheduler.requestSync()
    }

    /**
     * Reads the extra and *removes* it. Without the removal, a rotation re-runs
     * onCreate against the very same Intent and pushes ReminderDetail onto the
     * restored back stack a second time.
     */
    private fun Intent.consumeReminderId(): Long? {
        val id = getLongExtra(EXTRA_REMINDER_ID, -1L).takeIf { it > 0L } ?: return null
        removeExtra(EXTRA_REMINDER_ID)
        return id
    }

    companion object {
        const val EXTRA_REMINDER_ID = "com.stackpointer.lists.extra.REMINDER_ID"
    }
}
