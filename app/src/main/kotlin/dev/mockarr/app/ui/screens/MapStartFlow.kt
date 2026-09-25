package dev.mockarr.app.ui.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * How Start begins a drive, kept out of the Map screen's layout: the permission gate,
 * the drive-in choice (held spot or my location, Strava's Pause → Resume / Finish move),
 * the one-stop prompts that route from where you are, and the spinner while the real
 * location resolves. Every session read happens AT CLICK TIME via `.value`, never from
 * a composition capture, so the choice appears whichever order the route and the hold
 * were made in (a stale capture once ate the prompt).
 */
@Stable
internal class MapStartFlow(
    private val context: Context,
    private val scope: CoroutineScope,
    private val viewModel: MapViewModel,
    private val sessionViewModel: MockSessionViewModel,
    routeFromHoldPrompt: MutableState<Boolean>,
) {
    internal lateinit var playPermission: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>
    internal lateinit var locatePermission: ManagedActivityResultLauncher<String, Boolean>

    /** The real location is being read for a drive-in (Start shows a spinner). */
    var locating by mutableStateOf(false)
        private set

    /** One stop while holding: "route from the held spot?". Saveable, so it survives rotation. */
    var showRouteFromHoldPrompt by routeFromHoldPrompt

    /** One stop, not holding: "route from here?" at this real position. */
    var routeFromMePosition by mutableStateOf<LatLng?>(null)

    /** A prompt added the origin as the first stop: drive once the route has come back. */
    private var playWhenRouteReady = false
    private var routeAwaitingPermission: Route? = null
    private var originAwaitingPermission: LatLng? = null

    private fun currentHold(): MockSessionState.Holding? =
        sessionViewModel.session.value as? MockSessionState.Holding

    /** [from]: a drive-in origin (held spot / real location), driven into the route, never added to it. */
    fun requestPlay(route: Route, from: LatLng? = null) {
        val needed = context.missingMockPermissions()
        if (needed.isEmpty()) {
            // No pin teardown here: the service hands Holding → Playing off
            // without ever touching the test providers.
            play(route, from)
        } else {
            routeAwaitingPermission = route
            originAwaitingPermission = from
            playPermission.launch(needed.toTypedArray())
        }
    }

    private fun play(route: Route, from: LatLng?) {
        viewModel.setFollowCamera(true)
        val ui = viewModel.uiState.value
        sessionViewModel.play(route, ui.profile, ui.routeSaved, from)
    }

    internal fun onPlayPermission(grants: Map<String, Boolean>) {
        val route = routeAwaitingPermission
        val from = originAwaitingPermission
        routeAwaitingPermission = null
        originAwaitingPermission = null
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true && route != null) {
            play(route, from)
        } else {
            sessionViewModel.reportPermissionDenied()
        }
    }

    /** The action row's Start. */
    fun playOrAskStart() {
        val hold = currentHold()
        val ui = viewModel.uiState.value
        val route = ui.route
        when {
            route != null && hold != null -> {
                if (offersDriveIn(route.points.first(), hold.position)) {
                    viewModel.interaction.requestStartChoice(route)
                } else {
                    requestPlay(route)
                }
            }
            route != null -> askStartFromReal(route)
            ui.waypoints.size == 1 && hold != null -> showRouteFromHoldPrompt = true
            ui.waypoints.size == 1 -> askRouteFromReal()
            else -> Unit
        }
    }

    /** START FROM's two pills for [route]; [realStart] is set when the origin is my location. */
    fun startChoice(route: Route, realStart: LatLng?) = StartChoice(
        origin = if (realStart != null) StartOrigin.MY_LOCATION else StartOrigin.HELD_SPOT,
        // A drive-in, not a new first stop (Ethan, 2026-09-24): the route stays as it
        // is, so a saved route stays saved.
        onFromOrigin = {
            viewModel.interaction.clearStartChoice()
            val origin = realStart ?: currentHold()?.position
            if (origin != null) requestPlay(route, from = origin) else requestPlay(route)
        },
        onFromRouteStart = {
            viewModel.interaction.clearStartChoice()
            requestPlay(route)
        },
    )

    /** "Route from the held spot?" confirmed: the spot becomes the first stop, then the drive starts. */
    fun routeFromHold() {
        showRouteFromHoldPrompt = false
        currentHold()?.position?.let { hold ->
            viewModel.addWaypoint(hold, atStart = true)
            playWhenRouteReady = true
        }
    }

    /** "Route from here?" confirmed, as [routeFromHold] for the real position. */
    fun routeFromMe(realPosition: LatLng) {
        routeFromMePosition = null
        viewModel.addWaypoint(realPosition, atStart = true)
        playWhenRouteReady = true
    }

    internal fun onRouteSettled(route: Route?, routing: Boolean) {
        if (playWhenRouteReady && !routing && route != null) {
            playWhenRouteReady = false
            requestPlay(route)
        }
    }

    // One in-flight read at a time; the Start button shows a spinner meanwhile.
    private fun resolveRealLocation(onResolved: (LatLng?) -> Unit) {
        if (locating) return
        scope.launch {
            locating = true
            val real = viewModel.realLocation()
            locating = false
            onResolved(real)
        }
    }

    // Idle with a route: offer the device's real position as the origin when it
    // is away from the route start — while a session runs, "real" reads would
    // return the mocked fix, so this path exists only for an unmocked device.
    private fun askStartFromReal(route: Route) {
        if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // First run: requestPlay's own permission flow asks for location.
            requestPlay(route)
            return
        }
        resolveRealLocation { real ->
            val current = viewModel.uiState.value.route ?: return@resolveRealLocation
            if (real != null && offersDriveIn(current.points.first(), real)) {
                viewModel.interaction.requestStartChoice(current, realStart = real)
            } else {
                requestPlay(current)
            }
        }
    }

    // One stop and no hold: routing from the real position needs the permission
    // first; the locate launcher's grant pans to the user, and a second Start
    // continues here with the permission in hand.
    private fun askRouteFromReal() {
        if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            locatePermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        resolveRealLocation { real ->
            if (real != null) routeFromMePosition = real else sessionViewModel.reportLocateFailed()
        }
    }
}

/** The Map screen's [MapStartFlow], with its permission launchers registered in composition. */
@Composable
internal fun rememberStartFlow(viewModel: MapViewModel, sessionViewModel: MockSessionViewModel): MapStartFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val routeFromHoldPrompt = rememberSaveable { mutableStateOf(false) }
    val flow = remember { MapStartFlow(context, scope, viewModel, sessionViewModel, routeFromHoldPrompt) }
    flow.playPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        flow::onPlayPermission,
    )
    // Also the locate pill's gate: a grant pans to the user.
    flow.locatePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.locateReal() else sessionViewModel.reportLocatePermissionDenied()
    }
    LaunchedEffect(flow) {
        viewModel.uiState.collect { flow.onRouteSettled(it.route, it.isRouting) }
    }
    return flow
}
