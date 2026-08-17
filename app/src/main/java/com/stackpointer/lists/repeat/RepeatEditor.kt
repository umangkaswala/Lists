package com.stackpointer.lists.repeat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.stackpointer.lists.recurrence.Freq
import com.stackpointer.lists.recurrence.RRule
import com.stackpointer.lists.recurrence.rruleSummary
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The "Repeat" editor (design screen S10).
 *
 * Deliberately a standalone composable taking its initial value in and handing
 * a result back out, rather than owning a ViewModel or a nav route: right now
 * it's hosted as a sub-editor inside the Capture bottom sheet (which lives in
 * its own popup window and would be destroyed by navigating away), but Detail's
 * Repeat row will want it as a full-screen nav destination later. Keeping it
 * value-in/value-out means that move costs nothing.
 */
private enum class EndMode { NEVER, ON_DATE, AFTER_COUNT }

private val WEEK_ORDER = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
)

private val END_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@Composable
fun RepeatEditor(
    initial: RRule?,
    startDate: LocalDate,
    onCancel: () -> Unit,
    onSave: (RRule?) -> Unit
) {
    var freq by remember { mutableStateOf(initial?.freq ?: Freq.WEEKLY) }
    var interval by remember { mutableIntStateOf(initial?.interval ?: 1) }
    var byDay by remember { mutableStateOf(initial?.byDay ?: emptySet<DayOfWeek>()) }
    var endMode by remember {
        mutableStateOf(
            when {
                initial?.until != null -> EndMode.ON_DATE
                initial?.count != null -> EndMode.AFTER_COUNT
                else -> EndMode.NEVER
            }
        )
    }
    var untilDate by remember { mutableStateOf(initial?.until ?: startDate.plusYears(1)) }
    var count by remember { mutableIntStateOf(initial?.count ?: 10) }
    var showDatePicker by remember { mutableStateOf(false) }

    // BYDAY is only meaningful for WEEKLY; carrying a stale day selection into a
    // MONTHLY rule would serialize a rule the expander doesn't honour.
    //
    // BYMONTHDAY/BYMONTH have no controls on this screen (the design has none),
    // so they're carried through from the incoming rule rather than rebuilt —
    // otherwise opening "every month on the 15th" and pressing Save with no
    // edits would quietly downgrade it to "every month on the start date".
    val current = RRule(
        freq = freq,
        interval = interval,
        byDay = if (freq == Freq.WEEKLY) byDay else emptySet(),
        byMonthDay = initial?.byMonthDay?.takeIf { freq == Freq.MONTHLY || freq == Freq.YEARLY },
        byMonth = initial?.byMonth?.takeIf { freq == Freq.YEARLY },
        until = if (endMode == EndMode.ON_DATE) untilDate else null,
        count = if (endMode == EndMode.AFTER_COUNT) count else null
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = "Cancel")
            }
            Text(
                text = "Repeat",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            )
            Surface(
                onClick = { onSave(current) },
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = rruleSummary(current, startDate),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }

            SectionLabel("Frequency")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    Freq.DAILY to "Daily",
                    Freq.WEEKLY to "Weekly",
                    Freq.MONTHLY to "Monthly",
                    Freq.YEARLY to "Yearly"
                )
                options.forEachIndexed { index, (value, label) ->
                    SegmentButton(
                        label = label,
                        selected = freq == value,
                        shape = groupedShape(index, options.size),
                        modifier = Modifier.weight(1f)
                    ) { freq = value }
                }
            }

            SectionLabel("Every")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StepperButton(Icons.Rounded.Remove, "Less often", enabled = interval > 1) { interval-- }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = intervalLabel(interval, freq), style = MaterialTheme.typography.titleLarge)
                    }
                }
                // 99 is arbitrary but keeps the label readable and the expander's
                // search bounded; nobody needs "every 400 weeks".
                StepperButton(Icons.Rounded.Add, "More often", enabled = interval < 99) { interval++ }
            }

            if (freq == Freq.WEEKLY) {
                SectionLabel("On these days")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    WEEK_ORDER.forEachIndexed { index, day ->
                        SegmentButton(
                            label = day.getDisplayName(java.time.format.TextStyle.NARROW, Locale.ENGLISH),
                            selected = day in byDay,
                            shape = groupedShape(index, WEEK_ORDER.size),
                            modifier = Modifier.weight(1f)
                        ) {
                            byDay = if (day in byDay) byDay - day else byDay + day
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (byDay.isEmpty()) {
                        "No days picked — repeats on the same weekday as the reminder's date."
                    } else {
                        "Repeats on the days you picked."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionLabel("Ends")
            EndOptionRow(
                selected = endMode == EndMode.NEVER,
                label = "Never",
                onClick = { endMode = EndMode.NEVER },
                showDivider = false
            )
            EndOptionRow(
                selected = endMode == EndMode.ON_DATE,
                label = "On a date",
                onClick = { endMode = EndMode.ON_DATE }
            ) {
                Surface(
                    onClick = {
                        endMode = EndMode.ON_DATE
                        showDatePicker = true
                    },
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = untilDate.format(END_DATE_FORMAT),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            EndOptionRow(
                selected = endMode == EndMode.AFTER_COUNT,
                label = "After a number of times",
                onClick = { endMode = EndMode.AFTER_COUNT }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (count > 1) count-- }, enabled = endMode == EndMode.AFTER_COUNT) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Fewer times")
                    }
                    Text(text = "$count", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { if (count < 999) count++ }, enabled = endMode == EndMode.AFTER_COUNT) {
                        Icon(Icons.Rounded.Add, contentDescription = "More times")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = { onSave(null) },
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Don't repeat",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (showDatePicker) {
        UntilDatePickerDialog(
            initialDate = untilDate,
            onDismiss = { showDatePicker = false },
            onPick = {
                untilDate = it
                showDatePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UntilDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    // DatePicker speaks epoch millis at UTC midnight, not local midnight —
    // converting through the device's zone here would shift the date by a day
    // for anyone west of UTC.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) {
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                } else {
                    onDismiss()
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
}

/** M3 Expressive "connected button group" corners: round on the outside, tight within. */
private fun groupedShape(index: Int, size: Int): Shape = when {
    size == 1 -> RoundedCornerShape(24.dp)
    index == 0 -> RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    index == size - 1 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 24.dp, bottomEnd = 24.dp)
    else -> RoundedCornerShape(4.dp)
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = modifier.height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
}

@Composable
private fun EndOptionRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Column {
        if (showDivider) {
            Surface(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.fillMaxWidth().height(1.dp)
            ) {}
        }
        Surface(onClick = onClick, color = Color.Transparent) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
            }
        }
    }
}

private fun intervalLabel(interval: Int, freq: Freq): String {
    val unit = when (freq) {
        Freq.DAILY -> "day"
        Freq.WEEKLY -> "week"
        Freq.MONTHLY -> "month"
        Freq.YEARLY -> "year"
    }
    return if (interval == 1) unit else "$interval ${unit}s"
}
