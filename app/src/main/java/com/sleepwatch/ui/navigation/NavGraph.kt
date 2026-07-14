package com.sleepwatch.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sleepwatch.ui.home.HomeScreen
import com.sleepwatch.ui.achievements.AchievementsScreen
import com.sleepwatch.ui.settings.SettingsScreen
import com.sleepwatch.ui.statistics.StatisticsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Default.Home)
    data object Statistics : Screen("statistics", "统计", Icons.Default.BarChart)
    data object Achievements : Screen("achievements", "成就", Icons.Default.Star)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
}

val bottomNavItems = listOf(Screen.Home, Screen.Statistics, Screen.Achievements, Screen.Settings)

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Statistics.route) { StatisticsScreen() }
            composable(Screen.Achievements.route) { AchievementsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
