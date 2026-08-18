package com.stackpointer.lists.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpointer.lists.places.PlaceSearch
import com.stackpointer.lists.places.PlaceSuggestion
import com.stackpointer.lists.places.PlaceTrigger
import com.stackpointer.lists.ui.theme.ListsCorner
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The design's slider stops. Anything under 100 m is below what a geofence can hold. */
private val RADIUS_STOPS = listOf(100, 200, 500, 1000)

private val WINDOW_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** Design S08 — Capture, Where. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhereContent(
    state: CaptureUiState,
    onBack: () -> Unit,
    onSelectPlace: (Long?) -> Unit,
    onTriggerChange: (PlaceTrigger) -> Unit,
    onRadiusChange: (Int) -> Unit,
    onWindowChange: (Int?, Int?) -> Unit,
    onWindowDaysChange: (String?) -> Unit,
    onCreatePlace: (name: String, latitude: Double, longitude: Double, address: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val search = remember { PlaceSearch(context) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<PlaceSuggestion>()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    fun runSearch() {
        val text = query.trim()
        if (text.isEmpty()) return
        searching = true
        searchError = null
        scope.launch {
            val outcome = search.search(text)
            searching = false
            searched = true
            outcome
                .onSuccess { results = it }
                .onFailure {
                    results = emptyList()
                    searchError = it.message ?: "Could not look that up."
                }
        }
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Back")
            }
            Text(text = "Where", style = MaterialTheme.typography.titleLarge)
        }

        TriggerSelector(selected = state.placeTrigger, onSelect = onTriggerChange)

        Spacer(Modifier.height(16.dp))

        SectionLabel("Saved places")
        if (state.places.isEmpty()) {
            Text(
                text = "None saved yet — search for one below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                state.places.forEach { place ->
                    PlaceChip(
                        label = place.name,
                        selected = place.id == state.placeId,
                        onClick = {
                            // Tapping the selected chip clears it, so a place
                            // can be removed without hunting for a separate
                            // control.
                            onSelectPlace(if (place.id == state.placeId) null else place.id)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search a place") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = ::runSearch, enabled = query.isNotBlank()) { Text("Find") }
                }
            },
            shape = RoundedCornerShape(ListsCorner.large),
            modifier = Modifier.fillMaxWidth()
        )
        // No autocomplete-as-you-type: this is the device's own Geocoder (see
        // PLAN.md — no API key, no billing), and it is a network round trip per
        // call rather than a prefix index.
        Text(
            text = "Looked up on this phone. Type a name or address, then tap Find.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )

        searchError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (searched && !searching && results.isEmpty() && searchError == null) {
            Text(
                text = "Nothing found for that. Try a fuller address.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        results.forEach { suggestion ->
            SuggestionRow(
                suggestion = suggestion,
                onClick = {
                    onCreatePlace(
                        suggestion.name,
                        suggestion.latitude,
                        suggestion.longitude,
                        suggestion.address
                    )
                    query = ""
                    results = emptyList()
                    searched = false
                }
            )
        }

        if (state.hasPlace) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("How close")
            RadiusSlider(meters = state.placeRadiusMeters, onChange = onRadiusChange)
            Text(
                text = "Shared with every reminder on ${state.place?.name}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            OnlyBetweenRow(
                startMinute = state.placeWindowStartMinute,
                endMinute = state.placeWindowEndMinute,
                days = state.placeWindowDays,
                onChange = onWindowChange,
                onDaysChange = onWindowDaysChange
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    // Re-running the search when the sheet re-composes would fire a geocoder
    // lookup per keystroke of the *title* field; the search only ever runs from
    // the Find button.
    LaunchedEffect(state.placeId) {
        if (state.placeId != null) {
            results = emptyList()
            searched = false
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** The design's connected segmented button: 40 tall, corner 20 outer, 1dp outline. */
@Composable
private fun TriggerSelector(selected: PlaceTrigger, onSelect: (PlaceTrigger) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
        TriggerSegment(
            label = "When I arrive",
            icon = Icons.Rounded.Login,
            selected = selected == PlaceTrigger.ARRIVE,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                bottomStart = 20.dp,
                topEnd = ListsCorner.extraSmall,
                bottomEnd = ListsCorner.extraSmall
            ),
            onClick = { onSelect(PlaceTrigger.ARRIVE) },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.size(4.dp))
        TriggerSegment(
            label = "When I leave",
            icon = Icons.Rounded.Logout,
            selected = selected == PlaceTrigger.LEAVE,
            shape = RoundedCornerShape(
                topStart = ListsCorner.extraSmall,
                bottomStart = ListsCorner.extraSmall,
                topEnd = 20.dp,
                bottomEnd = 20.dp
            ),
            onClick = { onSelect(PlaceTrigger.LEAVE) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TriggerSegment(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = shape,
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.Check else icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(text = label, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/** Place chips: 40 tall, corner 8, tertiaryContainer when chosen. */
@Composable
private fun PlaceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        // tertiaryContainer, per the design's rule that place triggers are
        // tertiary everywhere in the app so they never read as time triggers.
        color = if (selected) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(ListsCorner.small),
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp).fillMaxSize()
        ) {
            Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: PlaceSuggestion, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(ListsCorner.large),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Rounded.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = suggestion.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                suggestion.address?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

/**
 * Snaps to the design's four stops rather than sliding freely: a geofence
 * radius of 337 m is not a more useful number than 500, and the stops are the
 * distances Google's own guidance treats as reliable.
 */
@Composable
private fun RadiusSlider(meters: Int, onChange: (Int) -> Unit) {
    val index = RADIUS_STOPS.indexOf(meters).takeIf { it >= 0 } ?: 1
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (meters >= 1000) "Within 1 km" else "Within $meters m",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = index.toFloat(),
            onValueChange = { onChange(RADIUS_STOPS[it.toInt().coerceIn(RADIUS_STOPS.indices)]) },
            valueRange = 0f..(RADIUS_STOPS.size - 1).toFloat(),
            steps = RADIUS_STOPS.size - 2,
            colors = SliderDefaults.colors()
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            RADIUS_STOPS.forEach { stop ->
                Text(
                    text = if (stop >= 1000) "1 km" else "$stop m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The design's "Only between" row. A geofence can't be given a schedule, so
 * this is a filter applied when the crossing is reported — see
 * [com.stackpointer.lists.places.isWithinPlaceWindow].
 */
@Composable
private fun OnlyBetweenRow(
    startMinute: Int?,
    endMinute: Int?,
    days: String?,
    onChange: (Int?, Int?) -> Unit,
    onDaysChange: (String?) -> Unit
) {
    val enabled = startMinute != null && endMinute != null
    var picking by remember { mutableStateOf<WindowEdge?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Only between", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Stops it alerting at 3 am",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    // A sensible waking-hours default beats making someone set
                    // both ends before the switch does anything.
                    if (on) onChange(8 * 60, 22 * 60) else onChange(null, null)
                }
            )
        }

        if (enabled) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                WindowButton(
                    label = "From",
                    minute = startMinute,
                    onClick = { picking = WindowEdge.START },
                    modifier = Modifier.weight(1f)
                )
                WindowButton(
                    label = "To",
                    minute = endMinute,
                    onClick = { picking = WindowEdge.END },
                    modifier = Modifier.weight(1f)
                )
            }
            // Equal ends mean the whole day, not one minute — see
            // isWithinPlaceWindow — so the two cases are worded separately.
            if (endMinute != null && startMinute != null && endMinute < startMinute) {
                WindowHint("This window runs overnight.")
            } else if (endMinute != null && startMinute != null && endMinute == startMinute) {
                WindowHint("Same start and end — this covers the whole day.")
            }

            DayFilterRow(days = days, onChange = onDaysChange)
        }
    }

    val edge = picking
    if (edge != null) {
        val current = if (edge == WindowEdge.START) startMinute else endMinute
        // Reuses the When editor's picker so the two agree on 12/24-hour
        // formatting and on what the dialog looks like.
        WhenTimePickerDialog(
            initialTime = LocalTime.ofSecondOfDay(((current ?: 480) * 60).toLong()),
            is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
            onDismiss = { picking = null },
            onPick = { time ->
                val minute = time.hour * 60 + time.minute
                if (edge == WindowEdge.START) onChange(minute, endMinute) else onChange(startMinute, minute)
                picking = null
            }
        )
    }
}

@Composable
private fun WindowHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** Two-letter codes, in the RRULE spelling the rest of the app already uses. */
private val DAY_ORDER = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

/**
 * Which days the window applies on. Every day selected is stored as null rather
 * than as all seven codes, so "no filter" has exactly one representation and
 * [com.stackpointer.lists.places.isWithinPlaceWindow] never has to special-case
 * a full list.
 */
@Composable
private fun DayFilterRow(days: String?, onChange: (String?) -> Unit) {
    val selected = remember(days) {
        days?.split(",")?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }?.toSet()
            ?: DAY_ORDER.toSet()
    }
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = "On these days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            DAY_ORDER.forEach { code ->
                val isOn = code in selected
                Surface(
                    onClick = {
                        val next = if (isOn) selected - code else selected + code
                        // Deselecting the last day would silence the reminder
                        // forever with nothing on screen to explain it, so an
                        // empty selection is read as "every day" instead.
                        onChange(
                            when {
                                next.isEmpty() -> null
                                next.size == DAY_ORDER.size -> null
                                else -> DAY_ORDER.filter { it in next }.joinToString(",")
                            }
                        )
                    },
                    color = if (isOn) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (isOn) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    border = if (isOn) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(ListsCorner.small),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(text = code.take(1), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private enum class WindowEdge { START, END }

@Composable
private fun WindowButton(
    label: String,
    minute: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(ListsCorner.large),
        modifier = modifier.height(56.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = LocalContentColor.current.copy(alpha = 0.8f)
            )
            Text(
                text = minute?.let {
                    LocalTime.ofSecondOfDay((it * 60).toLong())
                        .format(WINDOW_FORMAT)
                        .lowercase(Locale.ENGLISH)
                } ?: "Not set",
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/** A trailing close affordance for the summary chip, kept here with its siblings. */
@Composable
fun PlaceSummaryChip(label: String, onOpen: () -> Unit, onClear: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(ListsCorner.small),
        modifier = Modifier.height(32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f, fill = false)) {
                Surface(onClick = onOpen, color = Color.Transparent) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp).fillMaxSize()
                    ) {
                        Icon(
                            Icons.Rounded.Place,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove place",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
