package dev.mockarr.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import androidx.window.core.layout.WindowWidthSizeClass
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

    // Bottom bar on phones, navigation rail on wide screens (landscape/tablet).
    // Hand-rolled instead of NavigationSuiteScaffold so the bar can slide away
    // during playback (the suite only switches layout types, with no motion).
    val compactWidth = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
    val labels = topLevelDestinations.map { stringResource(it.labelRes) }
    val barItems: @Composable RowScope.() -> Unit = {
        topLevelDestinations.forEachIndexed { index, destination ->
            NavigationBarItem(
                selected = currentDestination?.hasRoute(destination.route::class) == true,
                onClick = { navController.navigateTopLevel(destination.route) },
                icon = { Icon(destination.icon, contentDescription = labels[index]) },
                label = { Text(labels[index]) },
            )
        }
    }
    val railItems: @Composable ColumnScope.() -> Unit = {
        topLevelDestinations.forEachIndexed { index, destination ->
            NavigationRailItem(
                selected = currentDestination?.hasRoute(destination.route::class) == true,
                onClick = { navController.navigateTopLevel(destination.route) },
                icon = { Icon(destination.icon, contentDescription = labels[index]) },
                label = { Text(labels[index]) },
            )
        }
    }
    AdaptiveNavigation(
        compactWidth = compactWidth,
        hidden = hideNavigation,
        barItems = barItems,
        railItems = railItems,
    ) {
        // The bar/rail inset themselves; the content keeps itself out from
        // under the status bar. When the bar is hidden, the Map sheet takes the
        // navigation-bar inset instead.
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

/**
 * Bottom navigation bar on compact widths, rail otherwise. [hidden] slides the
 * bar down (or the rail out) — the Map tab's sheet takes the bottom edge then.
 */
@Composable
private fun AdaptiveNavigation(
    compactWidth: Boolean,
    hidden: Boolean,
    barItems: @Composable RowScope.() -> Unit,
    railItems: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    if (compactWidth) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) { content() }
            AnimatedVisibility(
                visible = !hidden,
                enter = Motion.bottomChromeEnter,
                exit = Motion.bottomChromeExit,
            ) {
                NavigationBar { barItems() }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = !hidden, enter = Motion.floatingEnter, exit = Motion.floatingExit) {
                NavigationRail { railItems() }
            }
            Box(modifier = Modifier.weight(1f)) { content() }
        }
    }
}
