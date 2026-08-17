package com.stackpointer.lists.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stackpointer.lists.data.repository.ChecklistItemDraft
import com.stackpointer.lists.di.currentAppContainer
import com.stackpointer.lists.recurrence.rruleShortLabel
import com.stackpointer.lists.repeat.RepeatEditor
import kotlinx.coroutines.launch

@Composable
fun CaptureSheetContent(
    target: CaptureTarget,
    sheetKey: Int,
    onDismiss: () -> Unit
) {
    val container = currentAppContainer()
    val scope = rememberCoroutineScope()
    // Plain remember, not viewModel(): see CaptureViewModel's doc comment for
    // why. Keying on sheetKey (not just target) guarantees a fresh instance
    // every time the sheet opens, even for structurally-equal targets like
    // two back-to-back CaptureTarget.New() calls.
    val viewModel = remember(sheetKey) {
        CaptureViewModel(
            target = target,
            reminderRepository = container.reminderRepository,
            listRepository = container.listRepository,
            checklistRepository = container.checklistRepository,
            scope = scope
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onDismiss()
    }

    // A ModalBottomSheet renders in its own popup window, above the rest of
    // the screen — a SnackbarHost hosted outside it (e.g. in the NavHost) is
    // invisible while the sheet is open. This sheet needs its own.
    val snackbarHostState = remember { SnackbarHostState() }
    fun showStub(feature: String) {
        scope.launch { snackbarHostState.showSnackbar("$feature is coming in a later phase") }
    }

    Box {
        if (state.notFound) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    text = "This reminder no longer exists.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = onDismiss,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "Close", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                }
            }
            return
        }

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            when (state.mode) {
                CaptureMode.TYPING -> TypingContent(
                    state = state,
                    onTitleChange = viewModel::updateTitle,
                    onOpenWhen = viewModel::openWhen,
                    onClearDue = viewModel::clearDue,
                    onOpenRepeat = viewModel::openRepeat,
                    onClearRepeat = viewModel::clearRepeat,
                    onToggleChecklist = viewModel::toggleChecklist,
                    onChecklistTextChange = viewModel::updateChecklistItem,
                    onChecklistToggle = viewModel::toggleChecklistItem,
                    onChecklistRemove = viewModel::removeChecklistItem,
                    onChecklistAdd = viewModel::addChecklistItem,
                    onOpenListPicker = viewModel::openListPicker,
                    onStub = ::showStub,
                    onSend = viewModel::save
                )
                CaptureMode.WHEN -> WhenContent(
                    state = state,
                    onBack = viewModel::collapseToTyping,
                    onAllDayChange = viewModel::setAllDay,
                    onDueAtChange = viewModel::setDueAt,
                    onOpenRepeat = viewModel::openRepeat,
                    onStub = ::showStub
                )
                CaptureMode.REPEAT -> RepeatEditor(
                    initial = state.repeat,
                    startDate = state.repeatAnchorDate,
                    onCancel = viewModel::closeRepeat,
                    onSave = viewModel::setRepeat
                )
                CaptureMode.LIST -> ListPickerContent(
                    state = state,
                    onBack = viewModel::collapseToTyping,
                    onSelect = viewModel::selectList,
                    onCreate = viewModel::createListAndSelect
                )
            }
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypingContent(
    state: CaptureUiState,
    onTitleChange: (String) -> Unit,
    onOpenWhen: () -> Unit,
    onClearDue: () -> Unit,
    onOpenRepeat: () -> Unit,
    onClearRepeat: () -> Unit,
    onToggleChecklist: () -> Unit,
    onChecklistTextChange: (Int, String) -> Unit,
    onChecklistToggle: (Int) -> Unit,
    onChecklistRemove: (Int) -> Unit,
    onChecklistAdd: () -> Unit,
    onOpenListPicker: () -> Unit,
    onStub: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        TextField(
            value = state.title,
            onValueChange = onTitleChange,
            placeholder = { Text("What do you need to remember?") },
            textStyle = MaterialTheme.typography.titleLarge,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        val dueAt = state.dueAt
        val repeat = state.repeat
        if (dueAt != null || repeat != null) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (dueAt != null) {
                    CaptureChip(
                        icon = Icons.Rounded.Schedule,
                        label = formatDueLabel(dueAt, state.isAllDay),
                        clearDescription = "Clear due date",
                        onClick = onOpenWhen,
                        onClear = onClearDue
                    )
                }
                if (repeat != null) {
                    CaptureChip(
                        icon = Icons.Rounded.Repeat,
                        label = rruleShortLabel(repeat, state.repeatAnchorDate),
                        clearDescription = "Clear repeat",
                        onClick = onOpenRepeat,
                        onClear = onClearRepeat
                    )
                }
            }
        }

        if (state.showChecklist) {
            Spacer(Modifier.height(8.dp))
            ChecklistSection(
                items = state.checklist,
                onTextChange = onChecklistTextChange,
                onToggle = onChecklistToggle,
                onRemove = onChecklistRemove,
                onAdd = onChecklistAdd
            )
        }

        if (state.parsedFromText) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Read from what you typed · tap a chip to change it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CaptureActionIcon(Icons.Rounded.CalendarToday, "When", active = state.dueAt != null, onClick = onOpenWhen)
            CaptureActionIcon(Icons.Rounded.Place, "Where", onClick = { onStub("Places") })
            CaptureActionIcon(
                Icons.Rounded.Checklist,
                "Checklist",
                active = state.showChecklist,
                onClick = onToggleChecklist
            )
            CaptureActionIcon(Icons.Rounded.PhotoCamera, "Photo", onClick = { onStub("Photo attachments") })
            CaptureActionIcon(
                Icons.Rounded.List,
                "List",
                active = true,
                onClick = onOpenListPicker
            )
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = onSend,
                color = if (state.canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (state.canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Send, contentDescription = "Save reminder")
                }
            }
        }
    }
}

@Composable
private fun ChecklistSection(
    items: List<ChecklistItemDraft>,
    onTextChange: (Int, String) -> Unit,
    onToggle: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { onToggle(index) }) {
                    Icon(
                        imageVector = if (item.isCompleted) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = if (item.isCompleted) "Tick off" else "Not done yet",
                        tint = if (item.isCompleted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
                BasicTextField(
                    value = item.text,
                    onValueChange = { onTextChange(index, it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (item.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (item.text.isEmpty()) {
                            Text(
                                text = "List item",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                )
                IconButton(onClick = { onRemove(index) }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove item",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(onClick = onAdd, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    text = "Add an item",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ListPickerContent(
    state: CaptureUiState,
    onBack: () -> Unit,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit
) {
    var newListName by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Back")
            }
            Text(text = "List", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(8.dp))

        state.lists.forEach { list ->
            Surface(onClick = { onSelect(list.id) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(
                        imageVector = if (list.id == state.listId) {
                            Icons.Rounded.RadioButtonChecked
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (list.id == state.listId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(list.colorArgb))
                    )
                    Text(text = list.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }

        val pendingName = newListName
        if (pendingName == null) {
            Surface(
                onClick = { newListName = "" },
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(text = "New list", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = pendingName,
                    onValueChange = { newListName = it },
                    placeholder = { Text("List name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onCreate(pendingName) },
                    enabled = pendingName.isNotBlank()
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Create list")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CaptureChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    clearDescription: String,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = clearDescription, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CaptureActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun WhenContent(
    state: CaptureUiState,
    onBack: () -> Unit,
    onAllDayChange: (Boolean) -> Unit,
    onDueAtChange: (Long?) -> Unit,
    onOpenRepeat: () -> Unit,
    onStub: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Back")
            }
            Text(text = "When", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = "All day", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = state.isAllDay, onCheckedChange = onAllDayChange)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Quick pick",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        QuickTimeChips(onPick = onDueAtChange)

        Spacer(Modifier.height(20.dp))

        val repeat = state.repeat
        StubRow(
            label = "Repeat",
            value = if (repeat == null) "None" else rruleShortLabel(repeat, state.repeatAnchorDate),
            onClick = onOpenRepeat
        )
        StubRow(label = "Early alert", value = "At time of event", onClick = { onStub("Early alerts") })
        StubRow(label = "Alert style", value = "Default", onClick = { onStub("Alert style") }, showDivider = false)
    }
}

@Composable
private fun QuickTimeChips(onPick: (Long) -> Unit) {
    val presets = rememberQuickTimePresets()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { preset ->
            Surface(
                onClick = { onPick(preset.epochMillis) },
                color = androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StubRow(label: String, value: String, onClick: () -> Unit, showDivider: Boolean = true) {
    Column {
        Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(vertical = 0.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            }
        }
    }
}
