package com.stackpointer.lists.completed

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import com.stackpointer.lists.di.currentAppContainer
import com.stackpointer.lists.ui.selection.SelectionActionBar
import com.stackpointer.lists.ui.selection.SelectionTopBar
import com.stackpointer.lists.ui.theme.ListsCorner
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompletedScreen(onBack: () -> Unit, onOpenReminder: (Long) -> Unit) {
    val container = currentAppContainer()
    val viewModel: CompletedViewModel = viewModel(
        factory = CompletedViewModel.Factory(container.reminderRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var inSelectionMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmBinSelection by remember { mutableStateOf(false) }

    val allEntries = state.groups.flatMap { it.entries }
    val selectedEntries = allEntries.filter { it.completionId in selectedIds }

    fun leaveSelection() {
        inSelectionMode = false
        selectedIds = emptySet()
    }

    // Selection mode is a *mode*, so Back has to leave it rather than leaving
    // the screen — otherwise the only way out is the small close button.
    BackHandler(enabled = inSelectionMode) { leaveSelection() }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            if (inSelectionMode) {
                SelectionTopBar(
                    selectedCount = selectedIds.size,
                    onClose = ::leaveSelection,
                    onSelectAll = { selectedIds = allEntries.map { it.completionId }.toSet() }
                )
            } else {
                CompletedTopBar(
                    onBack = onBack,
                    onStartSelection = { inSelectionMode = true },
                    menuExpanded = showMenu,
                    onMenuChange = { showMenu = it },
                    onDeleteAllCompleted = {
                        showMenu = false
                        confirmDeleteAll = true
                    }
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    Column(modifier = Modifier.padding(bottom = 20.dp)) {
                        Text(
                            text = "Completed",
                            fontSize = 36.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.subtitle,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (state.chart.isNotEmpty()) {
                    item { SevenDayChart(state.chart) }
                }

                if (state.isEmpty) {
                    item { CompletedEmptyState() }
                }

                state.groups.forEach { group ->
                    item(key = "header-${group.label}") {
                        Text(
                            text = group.label,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                        )
                    }
                    itemsIndexedEntries(group) { index, entry ->
                        CompletedRow(
                            entry = entry,
                            shape = groupedShape(index == 0, index == group.entries.lastIndex),
                            isSelected = entry.completionId in selectedIds,
                            inSelectionMode = inSelectionMode,
                            onClick = {
                                if (inSelectionMode) {
                                    selectedIds = selectedIds.toggle(entry.completionId)
                                } else {
                                    onOpenReminder(entry.reminderId)
                                }
                            },
                            onLongClick = {
                                inSelectionMode = true
                                selectedIds = selectedIds + entry.completionId
                            },
                            onUndo = {
                                viewModel.undo(entry.completionId)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Put back on the list")
                                }
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    item(key = "gap-${group.label}") { Spacer(Modifier.height(12.dp)) }
                }

                if (state.canLoadMore) {
                    item {
                        TextButton(
                            onClick = viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Show older") }
                    }
                }
            }

            if (inSelectionMode) {
                SelectionActionBar(
                    primaryLabel = "Undo",
                    primaryIcon = Icons.Rounded.Undo,
                    onPrimary = {
                        // Two occurrences of the same repeating reminder are
                        // one reminder going back on the list, not two.
                        val count = selectedEntries.map { it.reminderId }.distinct().size
                        viewModel.undoAll(selectedEntries)
                        leaveSelection()
                        scope.launch {
                            snackbarHostState.showSnackbar("${plural(count, "reminder")} put back")
                        }
                    },
                    destructiveLabel = "Delete",
                    destructiveIcon = Icons.Rounded.Delete,
                    onDestructive = { confirmBinSelection = true },
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp).navigationBarsPadding()
                )
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all completed?") },
            // Spells out both halves. "Delete" everywhere else in this app means
            // recoverable, and a repeating reminder is not finished just because
            // one of its occurrences is — this dialog is the only place to say
            // so before either thing happens.
            text = {
                Text(
                    "Finished reminders move to the recycle bin, where they can be " +
                        "restored for 30 days. Repeating reminders keep running, but " +
                        "lose the history listed here."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    viewModel.deleteAllCompleted { result ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                when {
                                    result.binned == 0 && result.historyCleared == 0 ->
                                        "Nothing to delete"
                                    result.binned == 0 -> "History cleared"
                                    else -> "${plural(result.binned, "reminder")} " +
                                        "moved to the recycle bin"
                                }
                            )
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmBinSelection) {
        val reminderCount = selectedEntries.map { it.reminderId }.distinct().size
        val hasRepeating = selectedEntries.any { it.isRepeating }
        AlertDialog(
            onDismissRequest = { confirmBinSelection = false },
            title = { Text("Move to the recycle bin?") },
            // The rows are single occurrences but the action is on the whole
            // reminder. For a repeating series that means a reminder still due
            // in the future disappears — worth a sentence before it happens.
            text = {
                Text(
                    "${plural(reminderCount, "reminder")} will move to the recycle " +
                        "bin, and can be restored there for 30 days." +
                        if (hasRepeating) {
                            " That includes a repeating reminder, so its future " +
                                "occurrences stop as well."
                        } else {
                            ""
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.binEntries(selectedEntries)
                    confirmBinSelection = false
                    leaveSelection()
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "${plural(reminderCount, "reminder")} moved to the recycle bin"
                        )
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBinSelection = false }) { Text("Cancel") }
            }
        )
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

/**
 * `itemsIndexed` over a group's entries with stable keys. Extracted only
 * because the same three lines appear for every group and the key has to stay
 * unique across groups.
 */
private fun LazyListScope.itemsIndexedEntries(
    group: CompletedGroup,
    row: @Composable (Int, CompletedEntryUiModel) -> Unit
) {
    items(
        count = group.entries.size,
        key = { index -> "entry-${group.entries[index].completionId}" }
    ) { index ->
        row(index, group.entries[index])
    }
}

@Composable
private fun CompletedTopBar(
    onBack: () -> Unit,
    onStartSelection: () -> Unit,
    menuExpanded: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onDeleteAllCompleted: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onStartSelection, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Checklist, contentDescription = "Select reminders")
        }
        Box {
            IconButton(onClick = { onMenuChange(true) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuChange(false) }) {
                DropdownMenuItem(
                    text = { Text("Delete all completed") },
                    onClick = onDeleteAllCompleted
                )
            }
        }
    }
}

/**
 * The design's seven-day bar chart. Purely descriptive — no goal line, no
 * streak pressure, exactly as the spec asks.
 */
@Composable
private fun SevenDayChart(bars: List<CompletedDayBar>) {
    val maxCount = bars.maxOf { it.count }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(112.dp).padding(20.dp)
        ) {
            bars.forEach { bar ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    // The bar lives in its own weighted box rather than sitting
                    // directly above the label. Sized inline, a tall bar pushed
                    // its own label below the others' and the row of weekday
                    // letters came out stepped.
                    Box(
                        contentAlignment = Alignment.BottomCenter,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        // A zero day still gets a visible sliver: an absent bar
                        // and a bar of zero look identical otherwise, and one of
                        // those reads as "the chart is broken".
                        val fraction = if (maxCount == 0) 0f else bar.count.toFloat() / maxCount
                        val barHeight = MIN_BAR_HEIGHT + (MAX_BAR_HEIGHT - MIN_BAR_HEIGHT) * fraction
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight.dp)
                                .background(
                                    color = if (bar.isToday) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    shape = RoundedCornerShape(ListsCorner.small)
                                )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = bar.label,
                        fontSize = 11.sp,
                        fontWeight = if (bar.isToday) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (bar.isToday) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

private const val MIN_BAR_HEIGHT = 4f
private const val MAX_BAR_HEIGHT = 58f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompletedRow(
    entry: CompletedEntryUiModel,
    shape: Shape,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUndo: () -> Unit
) {
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            when {
                inSelectionMode && isSelected -> Icon(
                    Icons.Rounded.CheckBox,
                    contentDescription = "Selected",
                    modifier = Modifier.size(24.dp)
                )
                inSelectionMode -> Icon(
                    Icons.Rounded.CheckBoxOutlineBlank,
                    contentDescription = "Not selected",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )
                else -> Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textDecoration = TextDecoration.LineThrough,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2
                )
                Text(
                    text = entry.meta,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            // Hidden in selection mode: a per-row action inside a bulk-action
            // mode is just a way to undo the wrong thing.
            if (!inSelectionMode) {
                IconButton(onClick = onUndo, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.Undo,
                        contentDescription = "Put back on the list",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp)
    ) {
        Text(
            text = "Nothing completed yet",
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Tick a reminder off and it will show up here, with how close to " +
                "its due time you were.",
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp)
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
