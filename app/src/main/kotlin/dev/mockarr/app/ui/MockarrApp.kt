package dev.mockarr.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mockarr.app.ui.navigation.MapDestination
import dev.mockarr.app.ui.navigation.SavedRoutesDestination
import dev.mockarr.app.ui.navigation.SettingsDestination
import dev.mockarr.app.ui.navigation.SetupDestination
import dev.mockarr.app.ui.screens.MapScreen
import dev.mockarr.app.ui.screens.SavedRoutesScreen
import dev.mockarr.app.ui.screens.SettingsScreen
import dev.mockarr.app.ui.screens.SetupScreen
import kotlin.reflect.KClass

private data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(MapDestination, MapDestination::class, "Map", Icons.Filled.Place),
    TopLevelDestination(
        SavedRoutesDestination,
        SavedRoutesDestination::class,
        "Routes",
        Icons.AutoMirrored.Filled.List,
    ),
    TopLevelDestination(SettingsDestination, SettingsDestination::class, "Settings", Icons.Filled.Settings),
)

@Composable
fun MockarrApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    val selected = currentDestination?.hasRoute(destination.routeClass) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MapDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<MapDestination> {
                MapScreen(onOpenSetup = { navController.navigate(SetupDestination) })
            }
            composable<SavedRoutesDestination> {
                SavedRoutesScreen(
                    onRouteLoaded = {
                        navController.navigate(MapDestination) {
                            popUpTo(navController.graph.id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable<SettingsDestination> {
                SettingsScreen()
            }
            composable<SetupDestination> {
                SetupScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
