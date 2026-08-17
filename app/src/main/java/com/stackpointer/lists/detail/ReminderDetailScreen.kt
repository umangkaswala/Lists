package com.stackpointer.lists.detail

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.List
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
            container.listRepository
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notYetAvailable(feature: String) {
        scope.launch { snackbarHostState.showSnackbar("$feature is coming in a later phase") }
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
                    onSnooze = { notYetAvailable("Snooze") },
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.displaySmall,
                textDecoration = if (state.isCompleted) TextDecoration.LineThrough else null
            )

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
                        value = "None",
                        onClick = { notYetAvailable("Custom repeat") },
                        showDivider = false
                    )
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

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "History", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "This reminder doesn't repeat yet, so there's no streak to show.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant
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
