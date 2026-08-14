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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
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
import dev.mockarr.app.ui.screens.SetupViewModel

private data class TopLevelDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(MapDestination, "Map", Icons.Filled.Place),
    TopLevelDestination(SavedRoutesDestination, "Routes", Icons.AutoMirrored.Filled.List),
    TopLevelDestination(SettingsDestination, "Settings", Icons.Filled.Settings),
)

private fun NavDestination?.isTopLevel(): Boolean =
    this != null && topLevelDestinations.any { hasRoute(it.route::class) }

/**
 * Switch tabs. Overlays (e.g. Setup) don't belong in a tab's saved state — if
 * one is showing, pop back to a top-level destination first so tab restore
 * can never resurrect it.
 */
private fun NavController.navigateTopLevel(route: Any) {
    var popped = true
    while (popped && !currentDestination.isTopLevel() && previousBackStackEntry != null) {
        popped = popBackStack()
    }
    navigate(route) {
        popUpTo(graph.id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun MockarrApp(setupViewModel: SetupViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // First-run guidance: if mocking can't work yet, open the checklist once.
    var checkedOnLaunch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!checkedOnLaunch) {
            checkedOnLaunch = true
            if (!setupViewModel.isReadyToMock()) {
                navController.navigate(SetupDestination)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    val selected = currentDestination?.hasRoute(destination.route::class) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateTopLevel(destination.route) },
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
                    onRouteLoaded = { navController.navigateTopLevel(MapDestination) },
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
