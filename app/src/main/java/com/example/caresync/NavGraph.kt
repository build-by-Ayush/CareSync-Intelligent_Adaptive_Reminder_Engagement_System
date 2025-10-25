package com.example.caresync

import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import androidx.navigation.NavHostController

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable("welcome") {
            WelcomePage(navController)
        }
        composable("profile") {
            ProfileScreen(navController)
        }
        composable("main") {
            AppWithDrawer(navController) // ← PASS navController here
        }
        composable("analytics") {  // ← ADD THIS NEW ROUTE
            AnalyticsScreen(navController) // Your dashboard screen
        }
    }
}