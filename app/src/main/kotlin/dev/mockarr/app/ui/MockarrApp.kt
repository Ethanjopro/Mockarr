package dev.mockarr.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mockarr.app.R
import dev.mockarr.app.playback.MockSessionState
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
    val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(MapDestination, R.string.tab_map, Icons.Filled.Place),
    TopLevelDestination(SavedRoutesDestination, R.string.tab_routes, Icons.AutoMirrored.Filled.List),
    TopLevelDestination(SettingsDestination, R.string.tab_settings, Icons.Filled.Settings),
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
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    // While a drive plays on the Map tab the sheet owns the bottom edge; the
    // navigation bar returns with Stop (design brief: chrome recedes in playback).
    val hideNavigation = onMapTab && session is MockSessionState.Playing
    // navigationSuiteItems is not a composable scope — resolve labels here.
    val labels = topLevelDestinations.map { stringResource(it.labelRes) }

    // Bottom bar on phones, navigation rail on wide screens (landscape/tablet).
    NavigationSuiteScaffold(
        layoutType = if (hideNavigation) {
            NavigationSuiteType.None
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
        },
        navigationSuiteItems = {
            topLevelDestinations.forEachIndexed { index, destination ->
                val selected = currentDestination?.hasRoute(destination.route::class) == true
                val label = labels[index]
                item(
                    selected = selected,
                    onClick = { navController.navigateTopLevel(destination.route) },
                    icon = { Icon(destination.icon, contentDescription = label) },
                    label = { Text(label) },
                )
            }
        },
    ) {
        // NavigationSuiteScaffold insets only its own bar/rail; the content
        // must keep itself out from under the status bar (the bottom/side
        // insets are consumed by the suite's bar placement).
        val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(topInset)) {
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
                        onOpenSettings = { navController.navigateTopLevel(SettingsDestination) },
                        viewModel = mapViewModel,
                        sessionViewModel = sessionViewModel,
                        setupViewModel = setupViewModel,
                    )
                }
                composable<SavedRoutesDestination> {
                    OpaqueScreen {
                        SavedRoutesScreen(
                            onRouteLoaded = { navController.navigateTopLevel(MapDestination) },
                            onPlanDrive = {
                                mapViewModel.setBuilderMode(true)
                                navController.navigateTopLevel(MapDestination)
                            },
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
