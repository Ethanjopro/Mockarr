package dev.mockarr.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.app.ui.map.ActiveDwell
import dev.mockarr.app.ui.map.MockarrMap
import dev.mockarr.app.ui.map.PuckFix
import dev.mockarr.app.ui.map.effectiveStyleUrl
import dev.mockarr.app.ui.rememberSystemAnimationsEnabled
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.progressOrZero
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The persistent map itself — hosted by MockarrApp BEHIND the NavHost so it
 * survives tab switches. [visible] gates rendering and input while another
 * tab's opaque screen covers it.
 */
@Composable
fun MapLayer(
    viewModel: MapViewModel,
    sessionViewModel: MockSessionViewModel,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tileStyleUrl by viewModel.tileStyleUrl.collectAsStateWithLifecycle()
    val map3d by viewModel.map3dEnabled.collectAsStateWithLifecycle()
    val wobbleRadius by viewModel.wobbleRadiusMeters.collectAsStateWithLifecycle()
    val followCamera by viewModel.followCamera.collectAsStateWithLifecycle()
    val cameraCommand by viewModel.cameraCommand.collectAsStateWithLifecycle()
    val searchedPlace by viewModel.searchedPlace.collectAsStateWithLifecycle()
    val selectedWaypoint by viewModel.interaction.selectedWaypoint.collectAsStateWithLifecycle()
    val movingWaypoint by viewModel.interaction.movingWaypoint.collectAsStateWithLifecycle()
    val overlayBottomPx by viewModel.interaction.overlayBottomPx.collectAsStateWithLifecycle()
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    // Both change on every fix: read only inside the map's fix reader below, never in this body.
    val latestFix = sessionViewModel.latestFix.collectAsStateWithLifecycle()
    val dwell by sessionViewModel.dwell.collectAsStateWithLifecycle()
    val playbackState = sessionViewModel.playbackState.collectAsStateWithLifecycle()
    val drive by sessionViewModel.drive.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val playing = session is MockSessionState.Playing
    // While driving, the line is what the engine drives: a drive-in's lead-in, then the route.
    val shownRoute = if (playing) drive?.route ?: state.route else state.route
    // A drive's end clears its route (Ethan, 2026-09-24: the route kept for "Drive again"
    // is gone) — however it ended: arrival, End drive, or Stop in the notification while
    // the app was away. Hosted here, not in MapScreen — this layer stays composed while
    // Routes/Settings cover the map. drop(1): a stale outcome isn't a new end.
    LaunchedEffect(Unit) {
        sessionViewModel.driveOutcome.drop(1).filterNotNull().collect { viewModel.clearWaypoints() }
    }
    var holdAwaitingPermission by remember { mutableStateOf<LatLng?>(null) }
    val holdPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val position = holdAwaitingPermission
        holdAwaitingPermission = null
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true && position != null) {
            sessionViewModel.hold(position)
        } else {
            sessionViewModel.reportPermissionDenied()
        }
    }

    fun requestHold(position: LatLng) {
        val needed = context.missingMockPermissions()
        if (needed.isEmpty()) {
            sessionViewModel.hold(position)
        } else {
            holdAwaitingPermission = position
            holdPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    // Off-road spans split the drawn line into pieces, and line-progress is per piece.
    val shaded = playing && shownRoute?.offRoadSpans.isNullOrEmpty()
    // The last live progress: the engine's state goes null a beat before the hold, and the
    // shade must not snap back to the start meanwhile (a plain holder: the map reads it).
    val lastProgress = remember { floatArrayOf(0f) }
    val puckFix: () -> PuckFix? = remember(playing, shaded) {
        {
            if (playing) {
                val live = playbackState.value.takeUnless { it == null || it is PlaybackState.Finished }
                if (live != null) lastProgress[0] = live.progressOrZero.toFloat()
                val fix = latestFix.value
                PuckFix(fix?.position, fix?.truePosition, lastProgress[0].takeIf { shaded })
            } else {
                null
            }
        }
    }
    val displayed = remember(state.waypoints, state.route, state.routeIsFallback, state.routedFor) {
        displayWaypoints(state.waypoints, state.route, state.routeIsFallback, state.routedFor)
    }
    // The side panel (short windows) covers the map's start edge: fits and the follow
    // camera centre in the clear part to its right.
    val panelWidth = rememberSidePanelWidth()
    val startObstructionPx = with(LocalDensity.current) { panelWidth?.roundToPx() ?: 0 }
    MockarrMap(
        waypoints = displayed,
        routePoints = shownRoute?.points.orEmpty(),
        routeIsFallback = state.routeIsFallback,
        offRoadSpans = shownRoute?.offRoadSpans.orEmpty(),
        onMapTap = { point ->
            focusManager.clearFocus()
            // Dismiss-first: with the search open or a stop selected, a map tap
            // dismisses rather than dropping a new stop. Read .value at click
            // time (repo rule).
            if (!playing) {
                if (viewModel.interaction.searchOwnsTaps.value) {
                    // The search field/list is up: this tap only closes it.
                    viewModel.interaction.requestSearchDismiss()
                } else {
                    when (val action = viewModel.interaction.tapAction()) {
                        // A pending Move owns the next tap (Strava's Move Point); the wait survives.
                        is TapAction.MoveStop -> viewModel.moveStop(action.index, point, settled = true)
                        TapAction.DismissStartChoice -> viewModel.interaction.clearStartChoice()
                        TapAction.DismissSelection -> viewModel.interaction.select(null)
                        // Building or idle: a stop lands (the first one opens the builder).
                        TapAction.AddStop -> {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.addWaypoint(point)
                        }
                    }
                }
            } else if (viewModel.interaction.selectedWaypoint.value != null) {
                // Playback: a map tap only ever dismisses the stop popover.
                viewModel.interaction.select(null)
            }
        },
        onWaypointTap = { index ->
            focusManager.clearFocus()
            if (viewModel.interaction.movingWaypoint.value == null) {
                val toggled = index.takeIf { it != viewModel.interaction.selectedWaypoint.value }
                viewModel.interaction.select(toggled)
            }
        },
        onWaypointDrag = { index, point -> viewModel.moveStop(index, point, settled = false) },
        onWaypointDrop = { index, point -> viewModel.moveStop(index, point, settled = true) },
        onSelectedWaypointScreen = viewModel.interaction::setMarkerScreen,
        onMapLongPress = {
            focusManager.clearFocus()
            if (!playing) requestHold(it)
        },
        styleUrl = effectiveStyleUrl(tileStyleUrl, MockarrTheme.colors.isDark),
        palette = MockarrTheme.colors.map,
        visible = visible,
        loadInitialCamera = viewModel::initialCamera,
        onCameraIdle = { viewModel.cameraChanged(it, idle = true) },
        onCameraMove = { viewModel.cameraChanged(it, idle = false) },
        onUserGesture = {
            viewModel.setFollowCamera(false)
            viewModel.interaction.clearStartChoice()
            focusManager.clearFocus()
        },
        threeDimensional = map3d,
        selectedWaypoint = selectedWaypoint,
        activeDwell = dwell
            ?.takeIf { it.waypointIndex < state.waypoints.size }
            ?.let { ActiveDwell(it.waypointIndex, it.secondsLeft) },
        cameraCommand = cameraCommand,
        bottomObstructionPx = overlayBottomPx,
        startObstructionPx = startObstructionPx,
        pinPosition = (session as? MockSessionState.Holding)?.position,
        searchedPlace = searchedPlace?.position,
        onSearchPinTap = viewModel::addSearchedPlaceAsStop,
        puckFix = puckFix,
        wobbleRadiusMeters = wobbleRadius,
        cameraFollow = followCamera && playing,
        animateCamera = rememberSystemAnimationsEnabled(),
        dragEnabled = !playing,
        draggableWaypoint = movingWaypoint,
        modifier = modifier,
    )
}
