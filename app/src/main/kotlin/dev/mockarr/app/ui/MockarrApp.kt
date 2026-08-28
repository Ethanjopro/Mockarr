package dev.mockarr.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

/**
 * The app is one map. There is no navigation bar (Strava's Record screen is
 * the reference): Saved routes, Settings and Setup are pushed on top of the
 * Map from its sheet and popped by their back arrow or system back.
 */
@Composable
fun MockarrApp(setupViewModel: SetupViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Activity-scoped: the map layer and the Map screen's overlay must share
    // these exact instances (hiltViewModel() inside a destination would scope
    // a second copy to that backstack entry).
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

    val onMap = currentDestination == null || currentDestination.hasRoute(MapDestination::class)
    fun push(route: Any) = navController.navigate(route) { launchSingleTop = true }

    // The content keeps itself out from under the status bar only; the Map
    // sheet and the opaque screens take the navigation-bar inset themselves.
    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(topInset)) {
        // The map lives BEHIND the NavHost for the whole app lifetime — pushed
        // screens neither recreate it nor move its camera; they cover it with
        // an opaque Surface.
        MapLayer(
            viewModel = mapViewModel,
            sessionViewModel = sessionViewModel,
            visible = onMap,
            modifier = Modifier.fillMaxSize(),
        )
        NavHost(
            navController = navController,
            startDestination = MapDestination,
        ) {
            composable<MapDestination> {
                MapScreen(
                    onOpenSetup = { push(SetupDestination) },
                    onOpenRoutes = { push(SavedRoutesDestination) },
                    onOpenSettings = { push(SettingsDestination) },
                    viewModel = mapViewModel,
                    sessionViewModel = sessionViewModel,
                    setupViewModel = setupViewModel,
                )
            }
            composable<SavedRoutesDestination> {
                OpaqueScreen {
                    SavedRoutesScreen(
                        onBack = { navController.popBackStack() },
                        onRouteLoaded = { navController.popBackStack() },
                        onPlanDrive = {
                            mapViewModel.setBuilderMode(true)
                            navController.popBackStack()
                        },
                    )
                }
            }
            composable<SettingsDestination> {
                OpaqueScreen {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSetup = { push(SetupDestination) },
                    )
                }
            }
            composable<SetupDestination> {
                OpaqueScreen { SetupScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

/** Fully covers the persistent map layer while a pushed screen shows. */
@Composable
private fun OpaqueScreen(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.navigationBarsPadding()) { content() }
    }
}
