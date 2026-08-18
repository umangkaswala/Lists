package com.stackpointer.lists.bin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestoreFromTrash
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stackpointer.lists.data.repository.BIN_RETENTION_DAYS
import com.stackpointer.lists.di.currentAppContainer
import com.stackpointer.lists.ui.selection.SelectionActionBar
import com.stackpointer.lists.ui.selection.SelectionTopBar
import com.stackpointer.lists.ui.theme.ListsCorner
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(onBack: () -> Unit) {
    val container = currentAppContainer()
    val viewModel: RecycleBinViewModel = viewModel(
        factory = RecycleBinViewModel.Factory(container.reminderRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<Long>?>(null) }

    // The bin has no other purpose than acting on things, so selection is not a
    // separate mode here: the first tap selects, and the bar appears with it.
    val inSelectionMode = selectedIds.isNotEmpty()
    BackHandler(enabled = inSelectionMode) { selectedIds = emptySet() }

    // A row purged out from under the selection would otherwise leave its id
    // selected forever, and "2 selected" over one visible row.
    val visibleIds = state.entries.map { it.id }.toSet()
    val liveSelection = selectedIds.filter { it in visibleIds }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            if (inSelectionMode) {
                SelectionTopBar(
                    selectedCount = liveSelection.size,
                    onClose = { selectedIds = emptySet() },
                    onSelectAll = { selectedIds = visibleIds }
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Empty recycle bin") },
                                enabled = state.entries.isNotEmpty(),
                                onClick = {
                                    showMenu = false
                                    confirmDelete = state.entries.map { it.id }
                                }
                            )
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    Text(
                        text = "Recycle bin",
                        fontSize = 36.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                item { RetentionNotice() }

                if (state.isEmpty) {
                    item { BinEmptyState() }
                }

                itemsIndexed(state.entries, key = { _, entry -> entry.id }) { index, entry ->
                    BinRow(
                        entry = entry,
                        shape = groupedShape(index == 0, index == state.entries.lastIndex),
                        isSelected = entry.id in selectedIds,
                        onToggle = { selectedIds = selectedIds.toggle(entry.id) }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (inSelectionMode) {
                SelectionActionBar(
                    primaryLabel = "Restore",
                    primaryIcon = Icons.Rounded.RestoreFromTrash,
                    onPrimary = {
                        val count = liveSelection.size
                        viewModel.restore(liveSelection)
                        selectedIds = emptySet()
                        scope.launch {
                            snackbarHostState.showSnackbar(binPlural(count) + " restored")
                        }
                    },
                    destructiveLabel = "Delete now",
                    destructiveIcon = Icons.Rounded.DeleteForever,
                    onDestructive = { confirmDelete = liveSelection },
                    enabled = liveSelection.isNotEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp).navigationBarsPadding()
                )
            }
        }
    }

    val pendingDelete = confirmDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete permanently?") },
            // Named plainly: this is the one action in the app with no undo,
            // and the dialog is the last place it can be said.
            text = {
                Text(
                    "${binPlural(pendingDelete.size)} will be deleted for good. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteForever(pendingDelete)
                    confirmDelete = null
                    selectedIds = emptySet()
                    scope.launch {
                        snackbarHostState.showSnackbar(binPlural(pendingDelete.size) + " deleted")
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

private fun binPlural(count: Int): String =
    if (count == 1) "1 reminder" else "$count reminders"

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@Composable
private fun RetentionNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(ListsCorner.listGroupOuter),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            // The design's copy says "removed from every signed-in device".
            // There are no signed-in devices in this build — v1 is local-only —
            // so the sentence says what actually happens instead of promising
            // a sync that doesn't exist.
            Text(
                text = "Deleted reminders are kept for $BIN_RETENTION_DAYS days on this " +
                    "phone, then removed for good.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BinRow(
    entry: BinEntryUiModel,
    shape: Shape,
    isSelected: Boolean,
    onToggle: () -> Unit
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
            .combinedClickable(onClick = onToggle, onLongClick = onToggle)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.title, fontSize = 16.sp, lineHeight = 24.sp, maxLines = 2)
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
        }
    }
}

@Composable
private fun BinEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
    ) {
        Text(
            text = "The recycle bin is empty",
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Deleted reminders wait here for $BIN_RETENTION_DAYS days before " +
                "they are gone for good.",
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
