package com.stackpointer.lists.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stackpointer.lists.detail.ReminderDetailScreen
import com.stackpointer.lists.home.HomeScreen
import com.stackpointer.lists.lists.ListsScreen

object ListsDestinations {
    const val HOME = "home"
    const val LISTS = "lists"
    const val REMINDER_DETAIL = "reminder/{reminderId}"

    fun reminderDetail(reminderId: Long) = "reminder/$reminderId"
}

@Composable
fun ListsNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ListsDestinations.HOME) {
        composable(ListsDestinations.HOME) {
            HomeScreen(
                onOpenLists = { navController.navigate(ListsDestinations.LISTS) },
                onOpenReminder = { id -> navController.navigate(ListsDestinations.reminderDetail(id)) }
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
            ReminderDetailScreen(reminderId = reminderId, onBack = { navController.popBackStack() })
        }
    }
}
