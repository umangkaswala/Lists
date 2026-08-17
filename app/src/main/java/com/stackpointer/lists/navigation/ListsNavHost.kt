package com.stackpointer.lists.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.stackpointer.lists.home.HomeScreen
import com.stackpointer.lists.lists.ListsScreen

object ListsDestinations {
    const val HOME = "home"
    const val LISTS = "lists"
    const val REMINDER_DETAIL = "reminder/{reminderId}"

    fun reminderDetail(reminderId: Long) = "reminder/$reminderId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsNavHost() {
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

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = ListsDestinations.HOME) {
            composable(ListsDestinations.HOME) {
                HomeScreen(
                    onOpenLists = { navController.navigate(ListsDestinations.LISTS) },
                    onOpenReminder = { id -> navController.navigate(ListsDestinations.reminderDetail(id)) },
                    onOpenCapture = { target -> openCapture(target) }
                )
            }
            composable(ListsDestinations.LISTS) {
                ListsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = ListsDestinations.REMINDER_DETAIL,
                arguments = listOf(navArgument("reminderId") { type = NavType.LongType })
            ) { backStackEntry ->
                val reminderId = backStackEntry.arguments?.getLong("reminderId") ?: return@composable
                ReminderDetailScreen(
                    reminderId = reminderId,
                    onBack = { navController.popBackStack() },
                    onEdit = { openCapture(CaptureTarget.Edit(reminderId)) }
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
