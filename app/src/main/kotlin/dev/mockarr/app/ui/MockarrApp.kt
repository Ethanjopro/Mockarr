package dev.mockarr.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
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
import dev.mockarr.app.ui.screens.MapLayer
import dev.mockarr.app.ui.screens.MapScreen
import dev.mockarr.app.ui.screens.MapViewModel
import dev.mockarr.app.ui.screens.MockSessionViewModel
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

    // Activity-scoped: the map layer and the Map tab's overlay must share these
    // exact instances (hiltViewModel() inside a destination would scope a
    // second copy to that backstack entry).
    val mapViewModel: MapViewModel = hiltViewModel()
    val sessionViewModel: MockSessionViewModel = hiltViewModel()

    // First-run onboarding: the very first launch opens the setup checklist;
    // afterwards the map's not-ready banner (and Settings) are the way in.
    var checkedOnLaunch by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!checkedOnLaunch) {
            checkedOnLaunch = true
            if (setupViewModel.isFirstRun()) {
                setupViewModel.markSetupSeen()
                navController.navigate(SetupDestination)
            }
        }
    }

    val onMapTab = currentDestination == null || currentDestination.hasRoute(MapDestination::class)

    // Bottom bar on phones, navigation rail on wide screens (landscape/tablet).
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelDestinations.forEach { destination ->
                val selected = currentDestination?.hasRoute(destination.route::class) == true
                item(
                    selected = selected,
                    onClick = { navController.navigateTopLevel(destination.route) },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The map lives BEHIND the NavHost for the whole app lifetime —
            // tab switches neither recreate it nor move its camera. Non-map
            // destinations cover it with an opaque Surface.
            MapLayer(
                viewModel = mapViewModel,
                sessionViewModel = sessionViewModel,
                visible = onMapTab,
                modifier = Modifier.fillMaxSize(),
            )
            NavHost(
                navController = navController,
                startDestination = MapDestination,
            ) {
                composable<MapDestination> {
                    MapScreen(
                        onOpenSetup = { navController.navigate(SetupDestination) },
                        viewModel = mapViewModel,
                        sessionViewModel = sessionViewModel,
                        setupViewModel = setupViewModel,
                    )
                }
                composable<SavedRoutesDestination> {
                    OpaqueScreen {
                        SavedRoutesScreen(
                            onRouteLoaded = { navController.navigateTopLevel(MapDestination) },
                        )
                    }
                }
                composable<SettingsDestination> {
                    OpaqueScreen {
                        SettingsScreen(
                            onOpenSetup = { navController.navigate(SetupDestination) },
                        )
                    }
                }
                composable<SetupDestination> {
                    OpaqueScreen { SetupScreen(onBack = { navController.popBackStack() }) }
                }
            }
        }
    }
}

/** Fully covers the persistent map layer while a non-map destination shows. */
@Composable
private fun OpaqueScreen(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        content()
    }
}
