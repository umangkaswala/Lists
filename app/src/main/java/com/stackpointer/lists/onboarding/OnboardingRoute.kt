package com.stackpointer.lists.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stackpointer.lists.di.currentAppContainer
import kotlinx.coroutines.launch

/**
 * Stateful host for [OnboardingScreen]: owns the permission requests, and
 * records that onboarding has been dealt with so it doesn't reappear.
 *
 * The screen itself stays free of Android permission APIs, which is what lets
 * it be previewed and reasoned about as plain UI.
 */
@Composable
fun OnboardingRoute(onFinished: () -> Unit) {
    val context = LocalContext.current
    val container = currentAppContainer()

    var notificationsGranted by remember { mutableStateOf(PermissionState.hasNotifications(context)) }
    var exactAlarmsGranted by remember { mutableStateOf(PermissionState.hasExactAlarms(context)) }
    var locationGranted by remember { mutableStateOf(PermissionState.hasLocation(context)) }

    // Whether asking again would show a system dialog. Once the OS stops
    // showing one (two denials, or the "don't ask again" path), tapping the row
    // has to send the user to app settings or it would silently do nothing.
    var notificationsAskedAndDenied by remember { mutableStateOf(false) }
    var locationAskedAndDenied by remember { mutableStateOf(false) }

    fun refresh() {
        notificationsGranted = PermissionState.hasNotifications(context)
        exactAlarmsGranted = PermissionState.hasExactAlarms(context)
        locationGranted = PermissionState.hasLocation(context)
    }

    // Exact-alarm access is granted in system Settings, with no result callback
    // to listen for, so the only way to notice it is to re-read on resume. The
    // same sweep also catches a permission revoked while we were backgrounded.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        notificationsAskedAndDenied = !granted
    }

    // FINE and COARSE must be requested *together*. From Android 12 a request
    // for FINE alone is ignored outright — the dialog never appears and the
    // callback reports denied — so asking for one silently did nothing.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        locationGranted = granted
        locationAskedAndDenied = !granted
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    fun finish() {
        // The application scope, not this composable's: onFinished() navigates
        // away with popUpTo(inclusive), which disposes this composition — and
        // a rememberCoroutineScope() job would be cancelled with it, losing the
        // DataStore write and bringing onboarding back on the next launch.
        container.applicationScope.launch { container.onboardingStore.markCompleted() }
        onFinished()
    }

    OnboardingScreen(
        notificationsGranted = notificationsGranted,
        exactAlarmsGranted = exactAlarmsGranted,
        locationGranted = locationGranted,
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationsAskedAndDenied) {
                // Pre-33 there is no runtime permission to ask for: the switch
                // lives in system settings, so that's where the tap goes.
                settingsLauncher.launch(PermissionState.appSettingsIntent(context))
            } else {
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onRequestExactAlarms = {
            val intent = PermissionState.exactAlarmSettingsIntent(context)
            if (intent != null) settingsLauncher.launch(intent)
        },
        onRequestLocation = {
            if (locationAskedAndDenied) {
                settingsLauncher.launch(PermissionState.appSettingsIntent(context))
            } else {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        },
        onContinue = { finish() },
        onSkip = { finish() }
    )
}
