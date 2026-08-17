package com.stackpointer.lists.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stackpointer.lists.di.currentAppContainer
import com.stackpointer.lists.ui.theme.ListsCorner

@Composable
fun HomeScreen(
    onOpenLists: () -> Unit,
    onOpenReminder: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val container = currentAppContainer()
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(container.reminderRepository, container.listRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SearchBarRow(onOpenLists = onOpenLists)
            }

            item {
                TileGrid(state = state, onSelectList = viewModel::selectList)
            }

            if (state.listTiles.size > 1) {
                item {
                    FilterChipRow(state = state, onSelectList = viewModel::selectList)
                }
            }

            if (state.isEmpty) {
                item { HomeEmptyState() }
            }

            state.sections.forEach { section ->
                item {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (section.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                itemsIndexed(section.reminders) { index, reminder ->
                    ReminderCard(
                        reminder = reminder,
                        isFirst = index == 0,
                        isLast = index == section.reminders.lastIndex,
                        onToggleComplete = { viewModel.toggleCompleted(reminder.id, !reminder.isCompleted) },
                        onToggleImportant = { viewModel.toggleImportant(reminder.id, !reminder.isImportant) },
                        onClick = { onOpenReminder(reminder.id) }
                    )
                }
            }
        }

        CapturePill(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SearchBarRow(onOpenLists: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Search Lists",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenLists) {
                Icon(Icons.Rounded.List, contentDescription = "Your lists")
            }
        }
    }
}

@Composable
private fun TileGrid(state: HomeUiState, onSelectList: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "Today", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${state.overdueCount + state.todayCount}",
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    text = "${state.overdueCount} overdue · ${state.todayCount} to go",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (state.listTiles.isNotEmpty()) {
            val rows = state.listTiles.chunked(2)
            rows.forEach { rowTiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowTiles.forEach { tile ->
                        ListTile(
                            tile = tile,
                            selected = state.selectedListId == tile.id,
                            onClick = { onSelectList(tile.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ListTile(
    tile: ListTileUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(ListsCorner.largeIncreased),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(tile.colorArgb))
            )
            Spacer(Modifier.height(8.dp))
            Text(text = tile.name, style = MaterialTheme.typography.labelLarge)
            Text(text = "${tile.activeCount}", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun FilterChipRow(state: HomeUiState, onSelectList: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.listTiles.forEach { tile ->
            FilterChip(
                selected = state.selectedListId == tile.id,
                onClick = { onSelectList(tile.id) },
                label = { Text(tile.name) },
                shape = MaterialTheme.shapes.extraSmall,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

@Composable
private fun HomeEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp)
    ) {
        Text(text = "Nothing to remember yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap the capture pill below to add your first reminder.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: ReminderCardUiModel,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleComplete: () -> Unit,
    onToggleImportant: () -> Unit,
    onClick: () -> Unit
) {
    val outer = ListsCorner.listGroupOuter
    val inner = ListsCorner.listGroupInner
    val shape = RoundedCornerShape(
        topStart = if (isFirst) outer else inner,
        topEnd = if (isFirst) outer else inner,
        bottomStart = if (isLast) outer else inner,
        bottomEnd = if (isLast) outer else inner
    )
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            IconButton(onClick = onToggleComplete) {
                Icon(
                    imageVector = if (reminder.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (reminder.isCompleted) "Completed" else "Mark complete",
                    tint = if (reminder.isCompleted) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else null,
                    color = if (reminder.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (reminder.metaText != null) {
                    Text(
                        text = reminder.metaText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (reminder.isOverdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            IconButton(onClick = onToggleImportant) {
                Icon(
                    imageVector = if (reminder.isImportant) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (reminder.isImportant) "Important" else "Mark important",
                    tint = if (reminder.isImportant) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
fun CapturePill(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 8.dp,
        modifier = modifier
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
            IconButton(onClick = { /* wired in Phase 8: voice capture */ }) {
                Icon(Icons.Rounded.Mic, contentDescription = "Voice capture")
            }
            Text(
                text = "Add a reminder",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(ListsCorner.largeIncreased),
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = { /* wired in Phase 2: Capture sheet */ }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add reminder")
                }
            }
        }
    }
}
