package com.stackpointer.lists.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.Instant
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stackpointer.lists.di.currentAppContainer
import kotlinx.coroutines.launch

@Composable
fun ReminderDetailScreen(reminderId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val container = currentAppContainer()
    val viewModel: ReminderDetailViewModel = viewModel(
        factory = ReminderDetailViewModel.Factory(
            reminderId,
            container.reminderRepository,
            container.listRepository,
            container.checklistRepository,
            container.placeRepository
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // A minute-by-minute tick, so the overdue line appears while the screen is
    // open and its count keeps up. Reading the clock once inside the view
    // model's combine only refreshed it when the database changed.
    var clockTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            clockTick++
        }
    }
    val overdueText = remember(state.dueAtMillis, state.isCompleted, clockTick) {
        overdueLabel(state.dueAtMillis, state.isCompleted, Instant.now())
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notYetAvailable(feature: String) {
        scope.launch { snackbarHostState.showSnackbar("$feature is coming in a later phase") }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.weight(1f))
                if (state.found) {
                    IconButton(onClick = viewModel::toggleImportant) {
                        Icon(
                            imageVector = if (state.isImportant) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Important",
                            tint = if (state.isImportant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (state.found) {
                DetailBottomBar(
                    isCompleted = state.isCompleted,
                    onEdit = onEdit,
                    onSnooze = viewModel::snooze,
                    onShare = { notYetAvailable("Sharing") },
                    onDelete = { notYetAvailable("Deleting") },
                    onComplete = viewModel::toggleCompleted
                )
            }
        }
    ) { innerPadding ->
        if (!state.isLoading && !state.found) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("This reminder no longer exists.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // A reminder with a checklist, a note and history overflows a
                // phone screen; without this the bottom cards are unreachable.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.displaySmall,
                    textDecoration = if (state.isCompleted) TextDecoration.LineThrough else null
                )
                // Design S11's overdue line. Home and Today both flag an
                // overdue reminder in error; Detail, the screen you open to
                // find out about one, said nothing at all.
                overdueText?.let { overdue ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = overdue,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    PropertyRow(
                        icon = Icons.Rounded.CalendarToday,
                        label = "Due",
                        value = state.dueText ?: "No due date",
                        onClick = { notYetAvailable("Changing the date") }
                    )
                    PropertyRow(
                        icon = Icons.Rounded.List,
                        label = "List",
                        value = state.listName,
                        valueColor = Color(state.listColorArgb),
                        onClick = { notYetAvailable("Moving lists") }
                    )
                    PropertyRow(
                        icon = Icons.Rounded.Repeat,
                        label = "Repeat",
                        value = state.repeatText,
                        onClick = onEdit,
                        showDivider = state.placeText != null
                    )
                    // Only shown when there is one: an always-present "Place:
                    // None" row would put a place trigger in front of every
                    // user, including the ones who never want one.
                    state.placeText?.let { place ->
                        PropertyRow(
                            icon = Icons.Rounded.Place,
                            label = "Place",
                            value = place,
                            valueColor = MaterialTheme.colorScheme.tertiary,
                            onClick = onEdit,
                            showDivider = false
                        )
                    }
                }
            }

            if (state.checklist.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val done = state.checklist.count { it.isCompleted }
                        Text(
                            text = "CHECKLIST · $done of ${state.checklist.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        state.checklist.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleChecklistItem(item.id, !item.isCompleted) }
                                ) {
                                    Icon(
                                        imageVector = if (item.isCompleted) {
                                            Icons.Rounded.CheckCircle
                                        } else {
                                            Icons.Rounded.RadioButtonUnchecked
                                        },
                                        contentDescription = if (item.isCompleted) {
                                            "Tick off ${item.text}"
                                        } else {
                                            "${item.text} not done yet"
                                        },
                                        tint = if (item.isCompleted) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        }
                                    )
                                }
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                                    color = if (item.isCompleted) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val note = state.note
            if (!note.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "NOTE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = note, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            HistoryCard(state)
        }
    }
}

/**
 * The design's history card: "real evidence the app is working". It stays on
 * screen even with nothing in it, because an empty card that explains what will
 * appear reads better than a card that materialises out of nowhere the first
 * time something is completed.
 */
@Composable
private fun HistoryCard(state: ReminderDetailUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "History", style = MaterialTheme.typography.labelLarge)
            if (state.history.isEmpty()) {
                Text(
                    text = if (state.repeats) {
                        "Complete this once and every occurrence you finish will be listed here."
                    } else {
                        "Nothing completed yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Text(
                text = state.historySummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            state.history.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        // Late completions are still completions. Tinting them
                        // error would turn a history card into a scolding.
                        tint = if (item.wasOnTime) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = item.dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.punctualityText != null) {
                        Text(
                            text = item.punctualityText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (state.totalCompletions > state.history.size) {
                Text(
                    text = "Showing the most recent ${state.history.size} of " +
                        "${state.totalCompletions}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PropertyRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    valueColor: Color? = null,
    showDivider: Boolean = true
) {
    Column {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                // The label keeps its intrinsic width and the value takes the
                // rest: giving the *label* the weight instead squeezed it to one
                // character per line as soon as a value got long (e.g. a full
                // repeat summary).
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun DetailBottomBar(
    isCompleted: Boolean,
    onEdit: () -> Unit,
    onSnooze: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onComplete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(80.dp)
                .padding(horizontal = 12.dp)
        ) {
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit") }
            IconButton(onClick = onSnooze) { Icon(Icons.Rounded.Snooze, contentDescription = "Snooze") }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, contentDescription = "Share") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onComplete, shape = RoundedCornerShape(16.dp)) {
                Text(if (isCompleted) "Mark incomplete" else "Complete")
            }
        }
    }
}
