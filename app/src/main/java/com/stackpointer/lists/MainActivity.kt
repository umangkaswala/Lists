package com.stackpointer.lists

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stackpointer.lists.data.prefs.AppSettings
import com.stackpointer.lists.di.currentAppContainer
import com.stackpointer.lists.navigation.ListsNavHost
import com.stackpointer.lists.ui.theme.ListsTheme
import com.stackpointer.lists.ui.theme.isDark
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

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
            val container = currentAppContainer()

            // Null until the stored preferences have actually been read.
            // Rendering the default theme first and correcting it a frame later
            // is a visible white flash for anyone who has chosen Dark, so the
            // window simply stays on its themes.xml background until we know.
            val settings by produceState<AppSettings?>(initialValue = null, container) {
                value = container.settingsStore.settings.first()
                // Keep following it afterwards, so the Settings screen's own
                // toggles repaint the whole app live rather than on next launch.
                container.settingsStore.settings.collectLatest { value = it }
            }

            val current = settings ?: return@setContent
            val darkTheme = current.theme.isDark()

            // enableEdgeToEdge() was already called in onCreate to pick up the
            // *system* setting; this re-applies it whenever the user's own
            // choice disagrees. Without it, choosing Dark on a light phone
            // leaves dark status-bar icons on a dark bar — invisible.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle(darkTheme),
                    navigationBarStyle = systemBarStyle(darkTheme)
                )
                onDispose {}
            }

            ListsTheme(theme = current.theme, dynamicColour = current.dynamicColour) {
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

    /**
     * Transparent bars either way; the flag that matters is which set of icons
     * the system draws on top of them.
     */
    private fun systemBarStyle(darkTheme: Boolean): SystemBarStyle =
        if (darkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }

    companion object {
        const val EXTRA_REMINDER_ID = "com.stackpointer.lists.extra.REMINDER_ID"
    }
}
