package com.stackpointer.lists.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stackpointer.lists.capture.CaptureSheetContent
import com.stackpointer.lists.capture.CaptureTarget
import com.stackpointer.lists.detail.ReminderDetailScreen
import com.stackpointer.lists.di.currentAppContainer
import com.stackpointer.lists.home.HomeScreen
import com.stackpointer.lists.lists.ListsScreen
import com.stackpointer.lists.bin.RecycleBinScreen
import com.stackpointer.lists.completed.CompletedScreen
import com.stackpointer.lists.onboarding.OnboardingRoute
import com.stackpointer.lists.places.PlacesScreen
import com.stackpointer.lists.search.SearchScreen
import com.stackpointer.lists.settings.PrivacyScreen
import com.stackpointer.lists.settings.QuickTimesScreen
import com.stackpointer.lists.settings.SettingsScreen
import com.stackpointer.lists.today.TodayScreen
import kotlinx.coroutines.flow.first

object ListsDestinations {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LISTS = "lists"
    const val TODAY = "today"
    const val SEARCH = "search"
    const val COMPLETED = "completed"
    const val RECYCLE_BIN = "recycle_bin"
    const val PLACES = "places"
    const val SETTINGS = "settings"
    const val SETTINGS_QUICK_TIMES = "settings/quick_times"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val REMINDER_DETAIL = "reminder/{reminderId}"

    fun reminderDetail(reminderId: Long) = "reminder/$reminderId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsNavHost(
    pendingReminderId: Long? = null,
    onPendingReminderHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    var captureTarget by remember { mutableStateOf<CaptureTarget?>(null) }
    // CaptureTarget.New() instances are structurally equal to each other (data
    // class equals), so Compose's viewModel() call-site caching would hand back
    // a stale, already-saved CaptureViewModel on the next open. This counter
    // forces a fresh key every time a capture is opened, regardless of target
    // equality — see CLAUDE.md gotchas if this bites again.
    var captureRequestId by remember { mutableIntStateOf(0) }

    fun openCapture(target: CaptureTarget) {
        captureTarget = target
        captureRequestId++
    }

    // Read once, not observed: marking onboarding complete part-way through a
    // session must not swap the NavHost's start destination out from under the
    // back stack. Null means "not known yet" — one blank frame is better than
    // flashing Home and then jumping to onboarding.
    val container = currentAppContainer()
    val onboardingCompleted by produceState<Boolean?>(initialValue = null, container) {
        value = container.onboardingStore.isCompleted.first()
    }
    val startDestination = when (onboardingCompleted) {
        null -> null
        true -> ListsDestinations.HOME
        false -> ListsDestinations.ONBOARDING
    }

    // A tapped reminder notification. Waits for the start destination to be
    // resolved, otherwise the navigate() would race the NavHost's own creation.
    LaunchedEffect(pendingReminderId, startDestination) {
        if (pendingReminderId != null && startDestination != null) {
            navController.navigate(ListsDestinations.reminderDetail(pendingReminderId))
            onPendingReminderHandled()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (startDestination == null) return@Box
        NavHost(navController = navController, startDestination = startDestination) {
            composable(ListsDestinations.ONBOARDING) {
                OnboardingRoute(
                    onFinished = {
                        navController.navigate(ListsDestinations.HOME) {
                            popUpTo(ListsDestinations.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            composable(ListsDestinations.HOME) {
                HomeScreen(
                    onOpenLists = { navController.navigate(ListsDestinations.LISTS) },
                    onOpenReminder = { id -> navController.navigate(ListsDestinations.reminderDetail(id)) },
                    onOpenCapture = { target -> openCapture(target) },
                    onOpenSearch = { navController.navigate(ListsDestinations.SEARCH) },
                    onOpenToday = { navController.navigate(ListsDestinations.TODAY) },
                    onOpenCompleted = { navController.navigate(ListsDestinations.COMPLETED) },
                    onOpenRecycleBin = { navController.navigate(ListsDestinations.RECYCLE_BIN) },
                    onOpenPlaces = { navController.navigate(ListsDestinations.PLACES) },
                    onOpenSettings = { navController.navigate(ListsDestinations.SETTINGS) }
                )
            }
            composable(ListsDestinations.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenQuickTimes = {
                        navController.navigate(ListsDestinations.SETTINGS_QUICK_TIMES)
                    },
                    // The same Places screen the overflow menu reaches, rather
                    // than a second, lesser copy of it inside Settings.
                    onOpenPlaces = { navController.navigate(ListsDestinations.PLACES) },
                    onOpenPrivacy = { navController.navigate(ListsDestinations.SETTINGS_PRIVACY) }
                )
            }
            composable(ListsDestinations.SETTINGS_QUICK_TIMES) {
                QuickTimesScreen(onBack = { navController.popBackStack() })
            }
            composable(ListsDestinations.SETTINGS_PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
            composable(ListsDestinations.COMPLETED) {
                CompletedScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReminder = { id -> navController.navigate(ListsDestinations.reminderDetail(id)) }
                )
            }
            composable(ListsDestinations.PLACES) {
                PlacesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReminder = { id -> navController.navigate(ListsDestinations.reminderDetail(id)) }
                )
            }
            composable(ListsDestinations.RECYCLE_BIN) {
                RecycleBinScreen(onBack = { navController.popBackStack() })
            }
            composable(ListsDestinations.LISTS) {
                ListsScreen(onBack = { navController.popBackStack() })
            }
            composable(ListsDestinations.TODAY) {
                TodayScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReminder = { id -> navController.navigate(ListsDestinations.reminderDetail(id)) },
                    // Design S04: the pill "pre-fills today's date chip", so a
                    // reminder added from Today actually lands on Today rather
                    // than arriving with no date and never showing up here.
                    onAddReminder = { dueAt -> openCapture(CaptureTarget.New(prefillDueAt = dueAt)) },
                    onVoiceCapture = { spoken, dueAt ->
                        openCapture(CaptureTarget.New(prefillText = spoken, prefillDueAt = dueAt))
                    }
                )
            }
            composable(ListsDestinations.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReminder = { id -> navController.navigate(ListsDestinations.reminderDetail(id)) }
                )
            }
            composable(
                route = ListsDestinations.REMINDER_DETAIL,
                arguments = listOf(navArgument("reminderId") { type = NavType.LongType })
            ) { backStackEntry ->
                val reminderId = backStackEntry.arguments?.getLong("reminderId") ?: return@composable
                ReminderDetailScreen(
                    reminderId = reminderId,
                    onBack = { navController.popBackStack() },
                    onEdit = { mode -> openCapture(CaptureTarget.Edit(reminderId, mode)) }
                )
            }
        }
    }

    val target = captureTarget
    if (target != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { captureTarget = null },
            sheetState = sheetState
        ) {
            CaptureSheetContent(
                target = target,
                sheetKey = captureRequestId,
                onDismiss = { captureTarget = null }
            )
        }
    }
}
