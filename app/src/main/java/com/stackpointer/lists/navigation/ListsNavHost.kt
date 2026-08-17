package com.stackpointer.lists.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stackpointer.lists.home.HomeScreen

object ListsDestinations {
    const val HOME = "home"
}

@Composable
fun ListsNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ListsDestinations.HOME) {
        composable(ListsDestinations.HOME) {
            HomeScreen()
        }
    }
}
