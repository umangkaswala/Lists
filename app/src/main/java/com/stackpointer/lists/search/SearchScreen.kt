package com.stackpointer.lists.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stackpointer.lists.di.currentAppContainer
import com.stackpointer.lists.ui.theme.ListsCorner
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenReminder: (Long) -> Unit) {
    val container = currentAppContainer()
    val viewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.Factory(
            container.reminderRepository,
            container.checklistRepository,
            container.searchHistoryStore,
            container.attachmentRepository
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Dictation fills the query box rather than searching immediately, so a
    // misheard word can be corrected instead of returning nothing.
    val startVoiceSearch = com.stackpointer.lists.voice.rememberVoiceCaptureLauncher(
        prompt = "Say what you're looking for",
        onUnavailable = {
            scope.launch { snackbarHostState.showSnackbar("This phone has no dictation app") }
        },
        onResult = { spoken -> viewModel.onQueryChange(spoken) }
    )

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                SearchTopRow(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onClear = viewModel::onClearQuery,
                    onBack = onBack,
                    onSubmit = {
                        viewModel.submitSearch()
                        keyboardController?.hide()
                    },
                    onVoiceSearch = startVoiceSearch,
                    focusRequester = focusRequester
                )
                FilterChipsRow(
                    activeFilter = state.activeFilter,
                    onFilterSelected = viewModel::onFilterSelected
                )
            }
        }
    ) { innerPadding ->
        SearchBody(
            state = state,
            innerPadding = innerPadding,
            onToggleComplete = { id, completed -> viewModel.toggleCompleted(id, completed) },
            onOpenReminder = onOpenReminder,
            onRecentSelected = { text ->
                viewModel.onRecentQuerySelected(text)
                keyboardController?.hide()
            },
            onRemoveRecent = viewModel::removeRecentQuery,
            onClearRecent = viewModel::clearRecentSearches
        )
    }
}

@Composable
private fun SearchTopRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    onVoiceSearch: () -> Unit,
    focusRequester: FocusRequester
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    placeholder = { Text("Search reminders") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
                IconButton(onClick = onVoiceSearch) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Voice search")
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    activeFilter: SearchFilter?,
    onFilterSelected: (SearchFilter) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        SearchFilterChip(
            label = "Open",
            selected = activeFilter == SearchFilter.OPEN,
            onClick = { onFilterSelected(SearchFilter.OPEN) }
        )
        SearchFilterChip(
            label = "Completed",
            selected = activeFilter == SearchFilter.COMPLETED,
            onClick = { onFilterSelected(SearchFilter.COMPLETED) }
        )
        SearchFilterChip(
            label = "Checklists",
            selected = activeFilter == SearchFilter.CHECKLISTS,
            onClick = { onFilterSelected(SearchFilter.CHECKLISTS) }
        )
        SearchFilterChip(
            label = "Photos",
            selected = activeFilter == SearchFilter.PHOTOS,
            onClick = { onFilterSelected(SearchFilter.PHOTOS) }
        )
    }
}

@Composable
private fun SearchFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        shape = MaterialTheme.shapes.extraSmall,
        // The design gives the active chip secondaryContainer and leaves the
        // rest transparent with an outline. primaryContainer is reserved here
        // for the matched-text highlight — reusing it on chips would make the
        // two read as the same thing.
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        border = if (selected) null else FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = false,
            borderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun SearchBody(
    state: SearchUiState,
    innerPadding: PaddingValues,
    onToggleComplete: (Long, Boolean) -> Unit,
    onOpenReminder: (Long) -> Unit,
    onRecentSelected: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!state.hasSearched) {
            if (state.recentQueries.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = "Recent searches",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearRecent) { Text("Clear") }
                    }
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp)
                    ) {
                        state.recentQueries.forEach { recent ->
                            Surface(
                                // Design uses surfaceContainerHigh with an
                                // 8dp corner for these, not a pill.
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.extraSmall,
                                // Long-press removes one chip, per design S12.
                                // Without it the only way to drop a single
                                // mistyped search was to clear the whole
                                // history.
                                // clip() first: Material3's own surface
                                // modifier clips last, so a clickable added
                                // here would paint a square ripple over the
                                // 8dp corner.
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .combinedClickable(
                                        role = Role.Button,
                                        onClick = { onRecentSelected(recent) },
                                        onLongClickLabel = "Remove from recent searches",
                                        onLongClick = { onRemoveRecent(recent) }
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(text = recent, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Search by title, note or checklist item.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                val count = state.results.size
                Text(
                    text = if (count == 1) "1 result" else "$count results",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            if (state.results.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, start = 32.dp, end = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Names the filter that's doing the excluding. With
                        // Open selected by default, searching for something
                        // already ticked off otherwise reported that it simply
                        // doesn't exist.
                        Text(
                            text = when (state.activeFilter) {
                                SearchFilter.OPEN ->
                                    "No open reminders match \"${state.matchedQuery}\". " +
                                        "Try the Completed filter."
                                SearchFilter.COMPLETED ->
                                    "No completed reminders match \"${state.matchedQuery}\"."
                                SearchFilter.CHECKLISTS ->
                                    "No checklists match \"${state.matchedQuery}\"."
                                SearchFilter.PHOTOS ->
                                    "No reminders with a photo match " +
                                        "\"${state.matchedQuery}\"."
                                null -> "No reminders match \"${state.matchedQuery}\""
                            },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(state.results, key = { _, result -> result.id }) { index, result ->
                    SearchResultCard(
                        result = result,
                        query = state.matchedQuery,
                        isFirst = index == 0,
                        isLast = index == state.results.lastIndex,
                        onToggleComplete = { onToggleComplete(result.id, result.isCompleted) },
                        onClick = { onOpenReminder(result.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResultUiModel,
    query: String,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleComplete: () -> Unit,
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            IconButton(onClick = onToggleComplete) {
                Icon(
                    imageVector = if (result.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (result.isCompleted) "Completed" else "Mark complete",
                    tint = if (result.isCompleted) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = highlightedText(
                        text = result.title,
                        query = query,
                        highlightColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (result.isCompleted) TextDecoration.LineThrough else null,
                    color = if (result.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                when {
                    result.isCompleted -> {
                        result.completedText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    result.checklistPreview != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Checklist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = buildAnnotatedString {
                                    append("Checklist · ")
                                    append(
                                        highlightedText(
                                            text = result.checklistPreview,
                                            query = query,
                                            highlightColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (result.checklistTotal > 0) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(
                                    progress = { result.checklistDone.toFloat() / result.checklistTotal },
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    text = "${result.checklistDone} of ${result.checklistTotal}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    result.dueText != null -> {
                        Text(
                            text = result.dueText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Highlights every case-insensitive occurrence of [query] inside [text] with
 * [highlightColor] behind it. SQLite's LIKE is already case-insensitive for
 * ASCII, so matching here the same way keeps this in lockstep with what the
 * DAO actually matched — a query that found the row will always find
 * something to highlight in it.
 */
private fun highlightedText(text: String, query: String, highlightColor: Color) = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    // Search the original string with ignoreCase rather than indexing it with
    // offsets taken from a lowercased copy: lowercase() can change a string's
    // length (e.g. "İ" -> "i̇"), and those shifted offsets then index past the
    // end of the original and crash the whole results list.
    var cursor = 0
    while (cursor <= text.length) {
        val matchIndex = text.indexOf(query, cursor, ignoreCase = true)
        if (matchIndex == -1) {
            append(text.substring(cursor))
            break
        }
        append(text.substring(cursor, matchIndex))
        val matchEnd = (matchIndex + query.length).coerceAtMost(text.length)
        withStyle(SpanStyle(background = highlightColor)) {
            append(text.substring(matchIndex, matchEnd))
        }
        cursor = matchEnd
    }
}
