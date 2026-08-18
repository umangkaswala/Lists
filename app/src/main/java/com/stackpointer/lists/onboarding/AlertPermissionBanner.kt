package com.stackpointer.lists.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext

/**
 * Warns, on Home, when reminders physically cannot reach the user.
 *
 * This exists because the failure is completely silent otherwise: with
 * notifications denied or the channel muted, alarms still fire, the receiver
 * still runs, and `notify()` returns without doing anything. The user's
 * experience is simply "my reminders stopped working". Onboarding can be
 * skipped, and the system only shows its permission dialog twice ever, so a
 * one-shot prompt at first launch is not enough on its own.
 *
 * Renders nothing at all when alerts can be delivered.
 */
@Composable
fun AlertPermissionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var canDeliver by remember { mutableStateOf(PermissionState.canDeliverAlerts(context)) }
    var exactAlarms by remember { mutableStateOf(PermissionState.hasExactAlarms(context)) }
    var askedAndDenied by remember { mutableStateOf(false) }

    // These are changed in system Settings, so the only reliable moment to
    // re-read them is when the user comes back to the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDeliver = PermissionState.canDeliverAlerts(context)
                exactAlarms = PermissionState.hasExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        canDeliver = granted && PermissionState.canDeliverAlerts(context)
        askedAndDenied = !granted
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        canDeliver = PermissionState.canDeliverAlerts(context)
        exactAlarms = PermissionState.hasExactAlarms(context)
    }

    if (canDeliver && exactAlarms) return

    // Notifications off is the worse of the two — nothing arrives at all —
    // so it wins the banner when both are wrong.
    val notificationsAreTheProblem = !canDeliver

    val message = if (notificationsAreTheProblem) {
        "Notifications are off, so your reminders can't reach you."
    } else {
        "Without the alarms permission, reminders may arrive several minutes late."
    }
    val action = if (notificationsAreTheProblem) "Turn on" else "Fix"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.NotificationsOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        TextButton(
            onClick = {
                when {
                    !notificationsAreTheProblem ->
                        PermissionState.exactAlarmSettingsIntent(context)
                            ?.let { settingsLauncher.launch(it) }

                    // Once the system has stopped showing its dialog, asking
                    // again does nothing at all — the only way through is the
                    // settings screen.
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !askedAndDenied &&
                        !PermissionState.hasNotifications(context) ->
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)

                    else ->
                        settingsLauncher.launch(PermissionState.notificationSettingsIntent(context))
                }
            }
        ) {
            Text(
                text = action,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
