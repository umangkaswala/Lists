package com.stackpointer.lists.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stackpointer.lists.data.entity.ReminderListEntity
import com.stackpointer.lists.di.currentAppContainer
import kotlin.math.roundToInt

private val palette = listOf(
    Color(0xFFA03E28), Color(0xFF006A60), Color(0xFF6750A4),
    Color(0xFF9C4146), Color(0xFF4A6C2F), Color(0xFF3B608F)
)

@Composable
fun ListsScreen(onBack: () -> Unit) {
    val container = currentAppContainer()
    val viewModel: ListsViewModel = viewModel(
        factory = ListsViewModel.Factory(container.listRepository, container.reminderRepository)
    )
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val openCounts by viewModel.openCounts.collectAsStateWithLifecycle()

    var localOrder by remember { mutableStateOf(lists) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var showNewListDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ReminderListEntity?>(null) }

    LaunchedEffect(lists) {
        if (draggingId == null) localOrder = lists
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Your lists", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(localOrder, key = { it.id }) { list ->
                DraggableListRow(
                    list = list,
                    // Null while the counts are still loading; 0 only once we
                    // actually know the list is empty.
                    openCount = openCounts?.let { it[list.id] ?: 0 },
                    isDragging = draggingId == list.id,
                    onDragStart = { draggingId = list.id },
                    onDragEnd = {
                        draggingId = null
                        viewModel.reorder(localOrder)
                    },
                    onDragBy = { deltaPx, rowHeightPx ->
                        val fromIndex = localOrder.indexOfFirst { it.id == list.id }
                        if (fromIndex == -1) return@DraggableListRow
                        val toIndex = (fromIndex + (deltaPx / rowHeightPx).roundToInt())
                            .coerceIn(0, localOrder.lastIndex)
                        if (toIndex != fromIndex) {
                            localOrder = localOrder.toMutableList().apply {
                                add(toIndex, removeAt(fromIndex))
                            }
                        }
                    },
                    onDelete = { pendingDelete = list }
                )
            }
            item {
                Surface(
                    onClick = { showNewListDialog = true },
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "+ New list",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Shared with you", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "Coming soon — sharing needs an account system we haven't built yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showNewListDialog) {
        NewListDialog(
            onDismiss = { showNewListDialog = false },
            onCreate = { name, color ->
                viewModel.createList(name, color.toArgb())
                showNewListDialog = false
            }
        )
    }

    pendingDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${list.name}\"?") },
            text = { Text("Every reminder in this list will be deleted too.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteList(list)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DraggableListRow(
    list: ReminderListEntity,
    openCount: Int?,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragBy: (deltaPx: Float, rowHeightPx: Float) -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 64.dp.toPx() }
    var accumulated by remember { mutableFloatStateOf(0f) }

    Surface(
        color = if (isDragging) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.pointerInput(list.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { accumulated = 0f; onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount.y
                            onDragBy(accumulated, rowHeightPx)
                            if (kotlin.math.abs(accumulated) >= rowHeightPx) accumulated = 0f
                        }
                    )
                }
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(list.colorArgb))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = list.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = listOfNotNull(
                        "Default list".takeIf { list.isDefault },
                        when (openCount) {
                            null -> null
                            0 -> "Nothing to do"
                            1 -> "1 reminder"
                            else -> "$openCount reminders"
                        }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!list.isDefault) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Close, contentDescription = "Delete list")
                }
            }
        }
    }
}

@Composable
private fun NewListDialog(onDismiss: () -> Unit, onCreate: (String, Color) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(palette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New list") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (swatch == color) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { color = swatch }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, color) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
