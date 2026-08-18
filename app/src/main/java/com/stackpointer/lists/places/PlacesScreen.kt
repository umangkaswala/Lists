package com.stackpointer.lists.places

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stackpointer.lists.di.currentAppContainer
import com.stackpointer.lists.onboarding.PermissionState
import com.stackpointer.lists.ui.theme.ListsCorner

/** Design S05 — Places. */
@Composable
fun PlacesScreen(onBack: () -> Unit, onOpenReminder: (Long) -> Unit) {
    val container = currentAppContainer()
    val viewModel: PlacesViewModel = viewModel(
        factory = PlacesViewModel.Factory(container.placeRepository, container.geofenceRegistrar)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<PlaceGroupUiModel?>(null) }

    // Permission state has to be re-read, not remembered: the "Fix" button
    // leaves the app entirely, and the answer usually changes while we're gone.
    var permissionTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionTick++
                viewModel.refreshGeofences()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasPrecise = remember(permissionTick) { PermissionState.hasLocation(context) }
    val hasBackground = remember(permissionTick) { PermissionState.hasBackgroundLocation(context) }
    val approximateOnly = remember(permissionTick) {
        PermissionState.hasApproximateLocationOnly(context)
    }

    val requestForeground = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionTick++ }

    // Android 10 is the only version that will show a dialog for background
    // location. From Android 11 the request is denied on the spot without the
    // user seeing anything, so the banner sends them to Settings instead.
    val requestBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionTick++ }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(
                            text = "Places",
                            fontSize = 36.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitleFor(state),
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                permissionBanner(
                    hasPrecise = hasPrecise,
                    approximateOnly = approximateOnly,
                    hasBackground = hasBackground,
                    onRequestForeground = {
                        requestForeground.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onRequestBackground = {
                        if (PermissionState.canRequestBackgroundLocationInApp()) {
                            requestBackground.launch(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            )
                        } else {
                            context.startActivity(PermissionState.appSettingsIntent(context))
                        }
                    },
                    onOpenSettings = {
                        context.startActivity(PermissionState.appSettingsIntent(context))
                    }
                )

                // Says plainly that nothing is being tracked right now. The
                // permission banner above explains how to fix it; this says
                // what the cost is until it's fixed.
                if (state.geofenceStatus.missingPermission &&
                    state.geofenceStatus.requested > 0
                ) {
                    item {
                        Banner(
                            text = pluralReminders(state.geofenceStatus.requested) +
                                (if (state.geofenceStatus.requested == 1) " is" else " are") +
                                " paused until location access is granted.",
                            container = MaterialTheme.colorScheme.errorContainer,
                            onContainer = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (state.geofenceStatus.isNearLimit) {
                    item {
                        // The platform hard-stops at 100 per app and fails the
                        // *whole batch* on the 101st, so the warning has to
                        // arrive before the cliff, not at it.
                        Banner(
                            text = "You're tracking ${state.geofenceStatus.requested} places. " +
                                "Android only tracks $MAX_GEOFENCES at once — beyond that, " +
                                "the oldest stop working.",
                            container = MaterialTheme.colorScheme.errorContainer,
                            onContainer = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                state.geofenceStatus.lastError?.let { message ->
                    item {
                        Banner(
                            text = message,
                            container = MaterialTheme.colorScheme.errorContainer,
                            onContainer = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (state.isEmpty) {
                    item { PlacesEmptyState() }
                }

                state.groups.forEach { group ->
                    item(key = "place-${group.placeId}") {
                        PlaceGroupHeader(group = group, onDelete = { confirmDelete = group })
                    }
                    items(group.reminders, key = { "reminder-${it.id}" }) { reminder ->
                        PlaceReminderRow(reminder = reminder, onClick = { onOpenReminder(reminder.id) })
                        Spacer(Modifier.height(4.dp))
                    }
                    if (group.reminders.isEmpty()) {
                        item(key = "empty-${group.placeId}") {
                            Text(
                                text = "No reminders here yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )
                        }
                    }
                    item(key = "gap-${group.placeId}") { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    val pending = confirmDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${pending.name}?") },
            // Says what survives, because deleting a place looks like it might
            // take the reminders with it.
            text = {
                Text(
                    if (pending.reminders.isEmpty()) {
                        "This place isn't used by any reminders."
                    } else {
                        pluralReminders(pending.reminders.size) +
                            " will keep the reminder text but stop being triggered here."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlace(pending.placeId)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

private fun pluralReminders(count: Int): String =
    if (count == 1) "1 place reminder" else "$count place reminders"

private fun subtitleFor(state: PlacesUiState): String {
    if (state.isLoading) return ""
    val places = state.groups.size
    val triggers = state.totalTriggers
    val placeWord = if (places == 1) "1 place" else "$places places"
    val triggerWord = if (triggers == 1) "1 reminder" else "$triggers reminders"
    return "$placeWord · $triggerWord"
}

/**
 * Design S05's permission banner. Three different states, because they need
 * three different fixes and one generic "grant location" message would send the
 * user to the wrong place twice.
 */
private fun LazyListScope.permissionBanner(
    hasPrecise: Boolean,
    approximateOnly: Boolean,
    hasBackground: Boolean,
    onRequestForeground: () -> Unit,
    onRequestBackground: () -> Unit,
    onOpenSettings: () -> Unit
) {
    when {
        approximateOnly -> item {
            Banner(
                text = "Lists has approximate location only. Place reminders need " +
                    "precise location to know when you cross a boundary.",
                actionLabel = "Fix",
                onAction = onOpenSettings
            )
        }
        !hasPrecise -> item {
            Banner(
                text = "Place reminders need location access to know when you arrive " +
                    "or leave.",
                actionLabel = "Allow",
                onAction = onRequestForeground
            )
        }
        !hasBackground -> item {
            Banner(
                // Says "won't work", not "work less well". From Android 10,
                // registering a geofence *requires* background location — with
                // only foreground access nothing is registered at all, so the
                // softer wording promised something the app cannot do.
                //
                // "Allow all the time" is the exact wording of the system
                // settings option, so the instruction matches what the user
                // will be looking at.
                text = "Place reminders won't work until location access is set " +
                    "to \"Allow all the time\".",
                actionLabel = "Fix",
                onAction = onRequestBackground
            )
        }
    }
}

@Composable
private fun Banner(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.tertiaryContainer,
    onContainer: Color = MaterialTheme.colorScheme.onTertiaryContainer
) {
    Surface(
        color = container,
        contentColor = onContainer,
        shape = RoundedCornerShape(ListsCorner.listGroupOuter),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(12.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.size(12.dp))
                Surface(
                    onClick = onAction,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, onContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(ListsCorner.large),
                    modifier = Modifier.height(32.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        // fillMaxHeight, never fillMaxSize: inside a Row this
                        // button would otherwise demand the full width and
                        // squeeze the weighted message beside it down to one
                        // character per line.
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight()
                    ) {
                        Text(text = actionLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceGroupHeader(group: PlaceGroupUiModel, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)
    ) {
        Icon(
            Icons.Rounded.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = listOfNotNull(group.name, group.address).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "Delete ${group.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlaceReminderRow(reminder: PlaceReminderUiModel, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(ListsCorner.listGroupOuter),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = if (reminder.trigger == PlaceTrigger.ARRIVE) {
                    Icons.Rounded.Login
                } else {
                    Icons.Rounded.Logout
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = reminder.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(
                    text = reminder.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun PlacesEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
    ) {
        Text(
            text = "No places yet",
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Add a reminder, tap the pin icon, and search for somewhere. " +
                "Lists will tell you when you arrive or leave.",
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp)
        )
    }
}
