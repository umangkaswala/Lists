package com.stackpointer.lists.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stackpointer.lists.di.currentAppContainer
import com.stackpointer.lists.ui.theme.ListsCorner
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(onBack: () -> Unit, onOpenReminder: (Long) -> Unit, onAddReminder: () -> Unit) {
    val container = currentAppContainer()
    val viewModel: TodayViewModel = viewModel(
        factory = TodayViewModel.Factory(container.reminderRepository, container.checklistRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Hoisted at the screen level (not inside each row) so an in-flight
    // snapshot -> action -> snackbar -> undo sequence survives its row being
    // removed from the list mid-flight — e.g. completing a reminder rolls it
    // out of today's window entirely, unmounting the row while the "Undo"
    // snackbar is still up.
    val scope = rememberCoroutineScope()

    fun notYetAvailable(feature: String) {
        scope.launch { snackbarHostState.showSnackbar("$feature is coming in a later phase") }
    }

    fun handleSwipeAction(id: Long, direction: SwipeToDismissBoxValue, dismissState: SwipeToDismissBoxState) {
        if (direction == SwipeToDismissBoxValue.Settled) return
        scope.launch {
            val snapshot = viewModel.snapshotFor(id)
            val message = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    val rolledForward = viewModel.completeReminder(id)
                    if (rolledForward) "Moved to the next occurrence" else "Completed"
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    viewModel.snoozeReminder(id)
                    "Snoozed 30 minutes"
                }
                SwipeToDismissBoxValue.Settled -> return@launch
            }

            // Reset before the snackbar, not after. A reminder that merely
            // moves section (snoozed: Overdue -> Later today) keeps its row on
            // screen, and leaving it dismissed until the snackbar times out
            // showed an empty card with just the swipe background for ~4
            // seconds. Resetting here snaps it back immediately; the row then
            // re-renders wherever the new data puts it.
            dismissState.reset()

            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                snapshot?.let { viewModel.undo(it) }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TodayTopBar(
                subtitle = state.subtitle,
                onBack = onBack,
                onFilter = { notYetAvailable("Filtering") },
                onOverflow = { notYetAvailable("More options") }
            )
        },
        bottomBar = {
            AddToTodayPill(
                onMicClick = { notYetAvailable("Voice capture") },
                onAddClick = onAddReminder
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isEmpty) {
                item { TodayEmptyState() }
            }

            state.sections.forEach { section ->
                item {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (section.kind == TodaySectionKind.OVERDUE) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                if (section.kind == TodaySectionKind.COMPLETED) {
                    itemsIndexed(section.reminders, key = { _, reminder -> reminder.id }) { index, reminder ->
                        CompletedReminderCard(
                            reminder = reminder,
                            isFirst = index == 0,
                            isLast = index == section.reminders.lastIndex,
                            onClick = { onOpenReminder(reminder.id) }
                        )
                    }
                } else {
                    itemsIndexed(section.reminders, key = { _, reminder -> reminder.id }) { index, reminder ->
                        SwipeableReminderCard(
                            reminder = reminder,
                            isOverdue = section.kind == TodaySectionKind.OVERDUE,
                            isFirst = index == 0,
                            isLast = index == section.reminders.lastIndex,
                            onClick = { onOpenReminder(reminder.id) },
                            onSwipe = ::handleSwipeAction
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTopBar(subtitle: String, onBack: () -> Unit, onFilter: () -> Unit, onOverflow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onFilter) {
                Icon(Icons.Rounded.FilterList, contentDescription = "Filter")
            }
            IconButton(onClick = onOverflow) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
            }
        }
        Text(
            text = "Today",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun TodayEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp)
    ) {
        Text(text = "Nothing due today", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Overdue, later-today and completed reminders will show up here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun groupedShape(isFirst: Boolean, isLast: Boolean): Shape {
    val outer = ListsCorner.listGroupOuter
    val inner = ListsCorner.listGroupInner
    return RoundedCornerShape(
        topStart = if (isFirst) outer else inner,
        topEnd = if (isFirst) outer else inner,
        bottomStart = if (isLast) outer else inner,
        bottomEnd = if (isLast) outer else inner
    )
}

@Composable
private fun SwipeableReminderCard(
    reminder: TodayReminderCardUiModel,
    isOverdue: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onSwipe: (id: Long, direction: SwipeToDismissBoxValue, dismissState: SwipeToDismissBoxState) -> Unit
) {
    val shape = groupedShape(isFirst, isLast)
    val dismissState = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeActionBackground(direction = dismissState.targetValue, shape = shape) },
        onDismiss = { direction -> onSwipe(reminder.id, direction, dismissState) },
        modifier = Modifier.fillMaxWidth()
    ) {
        ReminderCardContent(reminder = reminder, isOverdue = isOverdue, shape = shape, onClick = onClick)
    }
}

@Composable
private fun SwipeActionBackground(direction: SwipeToDismissBoxValue, shape: Shape) {
    val isComplete = direction == SwipeToDismissBoxValue.StartToEnd
    val isSnooze = direction == SwipeToDismissBoxValue.EndToStart
    val containerColor = when {
        isComplete -> MaterialTheme.colorScheme.tertiaryContainer
        isSnooze -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        isComplete -> MaterialTheme.colorScheme.onTertiaryContainer
        isSnooze -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(containerColor)
            .padding(horizontal = 24.dp),
        contentAlignment = if (isSnooze) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isComplete) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = contentColor)
                Spacer(Modifier.width(8.dp))
                Text(text = "Complete", color = contentColor, style = MaterialTheme.typography.labelLarge)
            }
        } else if (isSnooze) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Snooze 30m", color = contentColor, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Snooze, contentDescription = null, tint = contentColor)
            }
        }
    }
}

@Composable
private fun ReminderCardContent(
    reminder: TodayReminderCardUiModel,
    isOverdue: Boolean,
    shape: Shape,
    onClick: () -> Unit
) {
    val containerColor = if (isOverdue) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (isOverdue) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val metaColor = if (isOverdue) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(text = reminder.title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
                val hasMeta = reminder.timeText != null || reminder.repeatLabel != null || reminder.hasChecklist
                if (hasMeta) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val timeText = reminder.timeText
                        if (timeText != null) {
                            MetaChip(icon = Icons.Rounded.Schedule, text = timeText, color = metaColor)
                        }
                        val repeatLabel = reminder.repeatLabel
                        if (repeatLabel != null) {
                            MetaChip(icon = Icons.Rounded.Repeat, text = repeatLabel, color = metaColor)
                        }
                        if (reminder.hasChecklist) {
                            Text(
                                text = "${reminder.checklistDone} of ${reminder.checklistTotal}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = metaColor
                            )
                        }
                    }
                }
            }
            if (reminder.isImportant) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Important",
                    tint = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun CompletedReminderCard(
    reminder: TodayReminderCardUiModel,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val shape = groupedShape(isFirst, isLast)
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.6f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Completed",
                tint = MaterialTheme.colorScheme.tertiary
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (reminder.hasChecklist) {
                    Text(
                        text = "${reminder.checklistDone} of ${reminder.checklistTotal}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val completedTimeText = reminder.completedTimeText
            if (completedTimeText != null) {
                Text(
                    text = completedTimeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddToTodayPill(onMicClick: () -> Unit, onAddClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 8.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            IconButton(onClick = onMicClick) {
                Icon(Icons.Rounded.Mic, contentDescription = "Voice capture")
            }
            Text(
                text = "Add to Today",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(ListsCorner.largeIncreased),
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add reminder")
                }
            }
        }
    }
}
