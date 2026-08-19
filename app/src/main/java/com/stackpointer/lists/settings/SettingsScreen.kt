package com.stackpointer.lists.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stackpointer.lists.capture.formatClock
import com.stackpointer.lists.capture.formatDuration
import com.stackpointer.lists.capture.quickTimePresets
import com.stackpointer.lists.data.prefs.QuickTimeSettings
import com.stackpointer.lists.data.prefs.ThemeChoice
import com.stackpointer.lists.data.repository.BIN_RETENTION_CHOICES
import com.stackpointer.lists.di.currentAppContainer
import com.stackpointer.lists.notifications.NotificationChannels
import com.stackpointer.lists.notifications.ReminderNudge
import com.stackpointer.lists.notifications.rememberAlertStyleSummary
import com.stackpointer.lists.capture.WhenTimePickerDialog
import com.stackpointer.lists.ui.theme.dynamicColourSupported
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Design S16.
 *
 * The screen's own rule, quoted from the spec: "Every row states its current
 * value in 14/20 onSurfaceVariant rather than hiding it behind a tap." So no
 * row here is a bare label — each one says what it is set to right now, which
 * is also why [SettingsViewModel.settings] starts null rather than at the
 * defaults.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenQuickTimes: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val container = currentAppContainer()
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            container.settingsStore,
            container.reminderExporter,
            container.reminderRepository,
            container.alarmScheduler,
            container.applicationScope,
            container.placeRepository
        )
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val placeNames by viewModel.placeNames.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The alert style lives in system Settings, not here, so it has to be
    // re-read when the user comes back. See rememberAlertStyleSummary.
    val alertStyle = rememberAlertStyleSummary()

    var showAllDayPicker by remember { mutableStateOf(false) }
    var showRetentionPicker by remember { mutableStateOf(false) }
    var showExportPicker by remember { mutableStateOf(false) }

    fun startActivitySafely(intent: Intent, failureMessage: String) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar(failureMessage) }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Settings",
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            val current = settings
            if (current == null) {
                // One blank frame rather than a screen full of values that
                // aren't the user's.
                Spacer(Modifier.weight(1f))
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    SectionHeader("Alerts")
                    SettingsCard {
                        SettingsRow(
                            title = "Alert style",
                            value = alertStyle,
                            onClick = {
                                startActivitySafely(
                                    NotificationChannels.channelSettingsIntent(context),
                                    "Couldn't open notification settings"
                                )
                            }
                        )
                        RowDivider()
                        SettingsRow(
                            title = "All-day reminders arrive at",
                            // "9:00 am", the design's own wording for this row —
                            // unlike the quick-time chips, which drop ":00".
                            value = LocalTime.ofSecondOfDay(current.allDayAlertMinuteOfDay * 60L)
                                .format(ALL_DAY_FORMAT)
                                .lowercase(Locale.ENGLISH),
                            onClick = { showAllDayPicker = true }
                        )
                        RowDivider()
                        SettingsSwitchRow(
                            title = "Nudge me again if ignored",
                            value = nudgeSummary(),
                            checked = current.nudgeWhenIgnored,
                            onCheckedChange = viewModel::setNudgeWhenIgnored
                        )
                        RowDivider()
                        // S16 draws this switch on. It cannot be: v1 is
                        // local-only, so there is no second device for an alert
                        // to be dismissed on. Shown and disabled rather than
                        // hidden, because the design's own answer to sync is
                        // "built but stubbed", and a row that says why is more
                        // use than a missing one.
                        SettingsSwitchRow(
                            title = "Dismiss on every device",
                            value = "Needs an account. Lists keeps everything on this phone.",
                            checked = false,
                            enabled = false,
                            onCheckedChange = {}
                        )
                    }
                }

                item {
                    SectionHeader("Capture")
                    SettingsCard {
                        SettingsSwitchRow(
                            title = "Read dates and places from my text",
                            value = "On device. Nothing is uploaded.",
                            checked = current.parseTypedText,
                            onCheckedChange = viewModel::setParseTypedText
                        )
                        RowDivider()
                        SettingsRow(
                            title = "Quick times",
                            value = quickTimePresets().joinToString(" · ") { it.label },
                            onClick = onOpenQuickTimes
                        )
                        RowDivider()
                        SettingsRow(
                            title = "Saved places",
                            value = placeNames?.let { names ->
                                if (names.isEmpty()) "None yet" else names.joinToString(", ")
                            } ?: "",
                            onClick = onOpenPlaces
                        )
                    }
                }

                item {
                    SectionHeader("Appearance")
                    SettingsCard {
                        ThemeSegments(
                            selected = current.theme,
                            onSelect = viewModel::setTheme
                        )
                        RowDivider()
                        SettingsSwitchRow(
                            title = "Colour from wallpaper",
                            value = when {
                                !dynamicColourSupported -> "Needs Android 12 or later"
                                current.dynamicColour -> "On — following your wallpaper"
                                else -> "Off — using the Lists palette"
                            },
                            checked = current.dynamicColour && dynamicColourSupported,
                            enabled = dynamicColourSupported,
                            onCheckedChange = viewModel::setDynamicColour
                        )
                    }
                }

                item {
                    SectionHeader("Data")
                    SettingsCard {
                        SettingsRow(
                            title = "Keep deleted items",
                            value = "${current.binRetentionDays} days",
                            onClick = { showRetentionPicker = true }
                        )
                        RowDivider()
                        SettingsRow(
                            title = "Export my reminders",
                            value = "JSON or plain text",
                            onClick = { showExportPicker = true }
                        )
                    }
                }

                // Not in the S16 mock-up. The plan requires a privacy page and
                // the design gives it nowhere to live, so it gets its own
                // section rather than being wedged into "Data", which is about
                // the user's reminders rather than about the app.
                item {
                    SectionHeader("About")
                    SettingsCard {
                        SettingsRow(
                            title = "Privacy",
                            value = "What stays on this phone",
                            onClick = onOpenPrivacy
                        )
                        RowDivider()
                        SettingsRow(
                            title = "Version",
                            value = appVersionName(),
                            onClick = null
                        )
                    }
                }
            }
        }
    }

    if (showAllDayPicker) {
        val current = settings
        WhenTimePickerDialog(
            initialTime = LocalTime.ofSecondOfDay((current?.allDayAlertMinuteOfDay ?: 540) * 60L),
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
            onDismiss = { showAllDayPicker = false },
            onPick = { time ->
                viewModel.setAllDayAlertMinuteOfDay(time.hour * 60 + time.minute)
                showAllDayPicker = false
            }
        )
    }

    if (showRetentionPicker) {
        ChoiceDialog(
            title = "Keep deleted items",
            // Said out loud, because shortening the window deletes things
            // for good the moment it is chosen, and nothing else on the screen
            // would warn them.
            supporting = "Anything already past the new limit is removed " +
                "straight away.",
            options = BIN_RETENTION_CHOICES.map { it to "$it days" },
            selected = settings?.binRetentionDays,
            onDismiss = { showRetentionPicker = false },
            onSelect = {
                viewModel.setBinRetentionDays(it)
                showRetentionPicker = false
            }
        )
    }

    if (showExportPicker) {
        ChoiceDialog(
            title = "Export my reminders",
            supporting = "Photos aren't included — they're files rather than " +
                "text. Deleted reminders aren't either.",
            options = ExportFormat.entries.map { it to it.label },
            selected = null,
            onDismiss = { showExportPicker = false },
            onSelect = { format ->
                showExportPicker = false
                viewModel.export(format) { intent ->
                    if (intent == null) {
                        scope.launch { snackbarHostState.showSnackbar("Couldn't write the export file") }
                    } else {
                        startActivitySafely(
                            Intent.createChooser(intent, "Export my reminders"),
                            "Couldn't open the share sheet"
                        )
                    }
                }
            }
        )
    }
}

/**
 * "Repeats about every 10 minutes, twice" — derived so the copy can't drift
 * from the code.
 *
 * "About" is doing real work. The nudge is an inexact alarm, so Android batches
 * it and it can land several minutes late (a 7½-minute window, measured on the
 * emulator). Spending the exact-alarm budget to be punctual about a reminder
 * the user is already ignoring would be the wrong trade — but the design's
 * flat "every 10 minutes" would then be a small lie.
 */
private fun nudgeSummary(): String {
    val minutes = ReminderNudge.INTERVAL_MILLIS / 60_000
    val times = if (ReminderNudge.MAX_ATTEMPTS == 2) "twice" else "${ReminderNudge.MAX_ATTEMPTS} times"
    return "Repeats about every $minutes minutes, $times"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        content()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * One row: title, its current value, and a chevron.
 *
 * [onClick] is nullable so a purely informational row (Version) can use the
 * same layout without pretending to be tappable.
 */
@Composable
private fun SettingsRow(title: String, value: String, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp)
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    value: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            // The whole row toggles, not just the switch — a 52 dp target at
            // the far edge of a 428 dp row is a long way to reach.
            .then(
                if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier
            )
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp)
            Text(
                text = value,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** S16: "Theme uses a connected 3-segment group, 40 tall, corners 20/4." */
@Composable
private fun ThemeSegments(selected: ThemeChoice, onSelect: (ThemeChoice) -> Unit) {
    Column(modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 20.dp)) {
        Text(text = "Theme", fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                ThemeChoice.LIGHT to "Light",
                ThemeChoice.DARK to "Dark",
                ThemeChoice.SYSTEM to "System"
            )
            options.forEachIndexed { index, (choice, label) ->
                val isSelected = choice == selected
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) 20.dp else 4.dp,
                    bottomStart = if (index == 0) 20.dp else 4.dp,
                    topEnd = if (index == options.lastIndex) 20.dp else 4.dp,
                    bottomEnd = if (index == options.lastIndex) 20.dp else 4.dp
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            shape = shape
                        )
                        .clickable { onSelect(choice) }
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

/** A single-choice dialog, used by the retention and export rows. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    supporting: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = supporting,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Text(text = label, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Whatever the manifest says, so the row can't drift from the actual build. */
@Composable
private fun appVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
}

/**
 * Design S16's "Quick times" sub-screen.
 *
 * Three rules rather than three fixed times, which is what makes the chips'
 * labels stay honest — see [com.stackpointer.lists.capture.quickTimePresets].
 */
@Composable
fun QuickTimesScreen(onBack: () -> Unit) {
    val container = currentAppContainer()
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            container.settingsStore,
            container.reminderExporter,
            container.reminderRepository,
            container.alarmScheduler,
            container.applicationScope,
            container.placeRepository
        )
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<QuickTimeField?>(null) }
    var showRelativePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Quick times",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        val current = settings ?: return@Column
        val quick = current.quickTimes

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "The three chips under the date and time fields when you " +
                    "set a reminder. Each one's label follows the time you pick.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            SettingsCard {
                SettingsRow(
                    title = "Soon",
                    value = "In ${formatDuration(quick.relativeMinutes)}",
                    onClick = { showRelativePicker = true }
                )
                RowDivider()
                SettingsRow(
                    title = "This evening",
                    value = formatClock(LocalTime.ofSecondOfDay(quick.eveningMinuteOfDay * 60L)),
                    onClick = { editing = QuickTimeField.EVENING }
                )
                RowDivider()
                SettingsRow(
                    title = "Tomorrow morning",
                    value = formatClock(LocalTime.ofSecondOfDay(quick.morningMinuteOfDay * 60L)),
                    onClick = { editing = QuickTimeField.MORNING }
                )
            }
            Text(
                text = "Right now they read: " +
                    quickTimePresets(quick, ZonedDateTime.now(ZoneId.systemDefault()))
                        .joinToString(" · ") { it.label },
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }

    val quick = settings?.quickTimes ?: QuickTimeSettings()
    val field = editing
    if (field != null) {
        val initial = when (field) {
            QuickTimeField.EVENING -> quick.eveningMinuteOfDay
            QuickTimeField.MORNING -> quick.morningMinuteOfDay
        }
        WhenTimePickerDialog(
            initialTime = LocalTime.ofSecondOfDay(initial * 60L),
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
            onDismiss = { editing = null },
            onPick = { time ->
                val minutes = time.hour * 60 + time.minute
                viewModel.setQuickTimes(
                    when (field) {
                        QuickTimeField.EVENING -> quick.copy(eveningMinuteOfDay = minutes)
                        QuickTimeField.MORNING -> quick.copy(morningMinuteOfDay = minutes)
                    }
                )
                editing = null
            }
        )
    }

    if (showRelativePicker) {
        ChoiceDialog(
            title = "Soon",
            supporting = "Measured from the moment you tap the chip.",
            options = RELATIVE_CHOICES.map { it to "In ${formatDuration(it)}" },
            selected = quick.relativeMinutes,
            onDismiss = { showRelativePicker = false },
            onSelect = {
                viewModel.setQuickTimes(quick.copy(relativeMinutes = it))
                showRelativePicker = false
            }
        )
    }
}

private enum class QuickTimeField { EVENING, MORNING }

private val RELATIVE_CHOICES = listOf(15, 30, 60, 120, 180)

private val ALL_DAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
