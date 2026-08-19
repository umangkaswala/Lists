package com.stackpointer.lists.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The Reminders channel's current alert style, kept fresh across a trip to
 * system Settings.
 *
 * Both places that show this — Settings S16's Alert style row and the Capture
 * sheet's When panel — send the user *out of the app* to change it, so a value
 * read once at composition is stale by the time they come back looking at it.
 * Shared rather than duplicated for exactly that reason: the second copy is
 * where the observer gets forgotten.
 */
@Composable
fun rememberAlertStyleSummary(): String {
    val context = LocalContext.current
    var summary by remember(context) {
        mutableStateOf(NotificationChannels.alertStyleSummary(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                summary = NotificationChannels.alertStyleSummary(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return summary
}
