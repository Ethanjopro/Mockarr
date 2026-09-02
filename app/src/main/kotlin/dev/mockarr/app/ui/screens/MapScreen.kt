package dev.mockarr.app.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.R
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.app.ui.Motion
import dev.mockarr.app.ui.Motion.fadeThrough
import dev.mockarr.app.ui.map.ActiveDwell
import dev.mockarr.app.ui.map.MockarrMap
import dev.mockarr.app.ui.map.NUDGE_TICK_MILLIS
import dev.mockarr.app.ui.map.ThumbstickOverlay
import dev.mockarr.app.ui.map.effectiveStyleUrl
import dev.mockarr.app.ui.map.nudgeMeters
import dev.mockarr.app.ui.rememberFormatter
import dev.mockarr.app.ui.rememberSystemAnimationsEnabled
import dev.mockarr.app.ui.theme.MapIconPill
import dev.mockarr.app.ui.theme.MapPill
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.Waypoint
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val followCamera by viewModel.followCamera.collectAsStateWithLifecycle()
    val cameraCommand by viewModel.cameraCommand.collectAsStateWithLifecycle()
    val selectedWaypoint by viewModel.interaction.selectedWaypoint.collectAsStateWithLifecycle()
    val movingWaypoint by viewModel.interaction.movingWaypoint.collectAsStateWithLifecycle()
    val overlayBottomPx by viewModel.interaction.overlayBottomPx.collectAsStateWithLifecycle()
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val latestFix by sessionViewModel.latestFix.collectAsStateWithLifecycle()
    val dwell by sessionViewModel.dwell.collectAsStateWithLifecycle()
    val playbackState by sessionViewModel.playbackState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val playing = session is MockSessionState.Playing
    // Natural arrival ends like Finish does: the driven route leaves the map (a
    // stay-at-destination hold keeps holding). Hosted here, not in MapScreen —
    // this layer stays composed while Routes/Settings cover the map.
    ArrivalEffect(session, playbackState) { viewModel.clearWaypoints() }
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

    val displayed = remember(state.waypoints, state.route, state.routeIsFallback, state.routedFor) {
        displayWaypoints(state.waypoints, state.route, state.routeIsFallback, state.routedFor)
    }
    MockarrMap(
        waypoints = displayed,
        routePoints = state.route?.points.orEmpty(),
        routeIsFallback = state.routeIsFallback,
        offRoadSpans = state.route?.offRoadSpans.orEmpty(),
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
                        TapAction.AddStop -> viewModel.addWaypoint(point)
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
        styleUrl = effectiveStyleUrl(tileStyleUrl, isSystemInDarkTheme()),
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
        pinPosition = (session as? MockSessionState.Holding)?.position,
        playbackPosition = if (playing) latestFix?.position else null,
        cameraFollow = followCamera && playing,
        animateCamera = rememberSystemAnimationsEnabled(),
        dragEnabled = !playing,
        draggableWaypoint = movingWaypoint,
        modifier = modifier,
    )
}

/**
 * The Map screen's controls — a transparent overlay above the persistent map,
 * laid out like Strava's Record screen: the search field and FAB stack float
 * at the top, the stat card floats above the sheet, and the sheet's peek is
 * the action row (Pause while driving), expanding to options and stops.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenSetup: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MapViewModel,
    sessionViewModel: MockSessionViewModel,
    setupViewModel: SetupViewModel,
    searchViewModel: MapSearchViewModel = hiltViewModel(),
    optionsViewModel: MapOptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val builderMode by viewModel.builderMode.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val options by optionsViewModel.settings.collectAsStateWithLifecycle()
    val sheetHintPending by optionsViewModel.sheetHintPending.collectAsStateWithLifecycle()
    var handleAnchor by remember { mutableStateOf(Offset.Zero) }
    val customServerConfigured by viewModel.customServerConfigured.collectAsStateWithLifecycle()
    val setupStatus by setupViewModel.status.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        setupViewModel.refresh()
        onPauseOrDispose { }
    }
    val units by viewModel.units.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val camera by viewModel.camera.collectAsStateWithLifecycle()
    LaunchedEffect(camera) { camera?.let { searchViewModel.cameraBias = it } }
    val map3d by viewModel.map3dEnabled.collectAsStateWithLifecycle()
    val followCamera by viewModel.followCamera.collectAsStateWithLifecycle()
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val playbackState by sessionViewModel.playbackState.collectAsStateWithLifecycle()
    val playbackError by sessionViewModel.error.collectAsStateWithLifecycle()
    val speedMultiplier by sessionViewModel.speedMultiplier.collectAsStateWithLifecycle()
    val selectedWaypoint by viewModel.interaction.selectedWaypoint.collectAsStateWithLifecycle()
    val movingWaypoint by viewModel.interaction.movingWaypoint.collectAsStateWithLifecycle()
    val markerScreen by viewModel.interaction.selectedMarkerScreen.collectAsStateWithLifecycle()
    val popoverWaypoint by viewModel.interaction.popoverWaypoint.collectAsStateWithLifecycle()
    val startChoiceRoute by viewModel.interaction.startChoiceRoute.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var routeAwaitingPermission by remember { mutableStateOf<Route?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val route = routeAwaitingPermission
        routeAwaitingPermission = null
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true && route != null) {
            viewModel.setFollowCamera(true)
            sessionViewModel.play(route)
        } else {
            sessionViewModel.reportPermissionDenied()
        }
    }
    val locatePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.locateReal() else sessionViewModel.reportLocatePermissionDenied()
    }
    // The sheet's "Hold my location at the map centre": the same permission gate as a long-press.
    var holdCentreAwaitingPermission by remember { mutableStateOf<LatLng?>(null) }
    val holdCentreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val position = holdCentreAwaitingPermission
        holdCentreAwaitingPermission = null
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true && position != null) {
            sessionViewModel.hold(position)
        } else {
            sessionViewModel.reportPermissionDenied()
        }
    }

    fun mapCentre(): LatLng? = viewModel.camera.value?.target

    fun holdAtCentre() {
        val centre = mapCentre() ?: return
        val needed = context.missingMockPermissions()
        if (needed.isEmpty()) {
            sessionViewModel.hold(centre)
        } else {
            holdCentreAwaitingPermission = centre
            holdCentreLauncher.launch(needed.toTypedArray())
        }
    }

    fun requestPlay(route: Route) {
        val needed = context.missingMockPermissions()
        if (needed.isEmpty()) {
            // No pin teardown here: the service hands Holding → Playing off
            // without ever touching the test providers.
            viewModel.setFollowCamera(true)
            sessionViewModel.play(route)
        } else {
            routeAwaitingPermission = route
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    val playing = session is MockSessionState.Playing
    val holding = session as? MockSessionState.Holding
    var showSaveDialog by remember { mutableStateOf(false) }
    // Save resolves the place name first (spinner), then opens the dialog with
    // it — the field never changes under the user (Ethan, session 17).
    var naming by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf<String?>(null) }
    fun beginSave() {
        if (naming) return
        scope.launch {
            naming = true
            saveName = viewModel.suggestName()
            naming = false
            showSaveDialog = true
        }
    }

    // Play while holding (or idle away from the route start): the action row
    // splits into "from held spot / from route start" — or "My location", the
    // same move for an unmocked device (Strava's Pause → Resume/Finish move).
    // All session reads happen AT CLICK TIME via .value — never from
    // composition-captured vals — so the choice appears no matter which order
    // route and hold were created in (a stale capture previously ate the prompt).
    var showRouteFromHoldPrompt by remember { mutableStateOf(false) }
    var routeFromMePosition by remember { mutableStateOf<LatLng?>(null) }
    var playWhenRouteReady by remember { mutableStateOf(false) }
    var pendingRealStart by remember { mutableStateOf<LatLng?>(null) }
    var locatingStart by remember { mutableStateOf(false) }

    fun currentHold(): MockSessionState.Holding? =
        sessionViewModel.session.value as? MockSessionState.Holding

    // One in-flight read at a time; the Start button shows a spinner meanwhile.
    fun resolveRealLocation(onResolved: (LatLng?) -> Unit) {
        if (locatingStart) return
        scope.launch {
            locatingStart = true
            val real = viewModel.realLocation()
            locatingStart = false
            onResolved(real)
        }
    }

    // Idle with a route: offer the device's real position as the origin when it
    // is away from the route start — while a session runs, "real" reads would
    // return the mocked fix, so this path exists only for an unmocked device.
    fun askStartFromReal(route: Route) {
        if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // First run: requestPlay's own permission flow asks for location.
            requestPlay(route)
            return
        }
        resolveRealLocation { real ->
            val current = viewModel.uiState.value.route ?: return@resolveRealLocation
            if (real == null || startsNear(current.points.first(), real)) {
                requestPlay(current)
            } else {
                pendingRealStart = real
                viewModel.interaction.requestStartChoice(current)
            }
        }
    }

    // One stop and no hold: routing from the real position needs the permission
    // first; the locate launcher's grant pans to the user, and a second Start
    // continues here with the permission in hand.
    fun askRouteFromReal() {
        if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            locatePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        resolveRealLocation { real ->
            if (real != null) routeFromMePosition = real else sessionViewModel.reportLocateFailed()
        }
    }

    fun playOrAskStart() {
        val hold = currentHold()
        val ui = viewModel.uiState.value
        val route = ui.route
        pendingRealStart = null
        when {
            route != null && hold != null -> {
                if (startsNear(route.points.first(), hold.position)) {
                    requestPlay(route)
                } else {
                    viewModel.interaction.requestStartChoice(route)
                }
            }
            route != null -> askStartFromReal(route)
            ui.waypoints.size == 1 && hold != null -> showRouteFromHoldPrompt = true
            ui.waypoints.size == 1 -> askRouteFromReal()
            else -> Unit
        }
    }

    LaunchedEffect(state.route, state.isRouting) {
        if (playWhenRouteReady && !state.isRouting) {
            state.route?.let { route ->
                playWhenRouteReady = false
                requestPlay(route)
            }
        }
    }

    val startChoice = startChoiceRoute?.let { pendingRoute ->
        val realStart = pendingRealStart
        StartChoice(
            origin = if (realStart != null) StartOrigin.MY_LOCATION else StartOrigin.HELD_SPOT,
            onFromOrigin = {
                viewModel.interaction.clearStartChoice()
                val origin = realStart ?: currentHold()?.position
                pendingRealStart = null
                origin?.let { viewModel.addWaypoint(it, atStart = true) }
                playWhenRouteReady = true
            },
            onFromRouteStart = {
                viewModel.interaction.clearStartChoice()
                pendingRealStart = null
                requestPlay(pendingRoute)
            },
        )
    }
    // Back unwinds the transient map states before anything else; the search
    // overlay is topmost — without this, Back under an open list left the app.
    val searchOpen = searchState.results.isNotEmpty() || searchState.status != MapSearchViewModel.Status.IDLE
    // The map layer (behind the NavHost) can't see this ViewModel: mirror
    // "search owns the next tap" onto the shared interaction object, and close
    // the search when the map reports it swallowed a tap for us.
    val searchOwnsTaps = searchOpen || searchState.fieldFocused
    LaunchedEffect(searchOwnsTaps) { viewModel.interaction.setSearchOwnsTaps(searchOwnsTaps) }
    LaunchedEffect(Unit) {
        viewModel.interaction.searchDismissTicks.drop(1).collect {
            focusManager.clearFocus()
            searchViewModel.clearResults()
        }
    }
    BackHandler(enabled = searchOpen || startChoice != null || movingWaypoint != null || selectedWaypoint != null) {
        when {
            searchOpen -> {
                focusManager.clearFocus()
                searchViewModel.clearResults()
            }
            startChoice != null -> {
                pendingRealStart = null
                viewModel.interaction.clearStartChoice()
            }
            movingWaypoint != null -> viewModel.interaction.cancelMove()
            else -> viewModel.interaction.select(null)
        }
    }

    if (showRouteFromHoldPrompt) {
        RouteFromHoldDialog(
            onConfirm = {
                showRouteFromHoldPrompt = false
                currentHold()?.position?.let { hold ->
                    viewModel.addWaypoint(hold, atStart = true)
                    playWhenRouteReady = true
                }
            },
            onDismiss = { showRouteFromHoldPrompt = false },
        )
    }
    routeFromMePosition?.let { realPosition ->
        RouteFromMeDialog(
            onConfirm = {
                routeFromMePosition = null
                viewModel.addWaypoint(realPosition, atStart = true)
                playWhenRouteReady = true
            },
            onDismiss = { routeFromMePosition = null },
        )
    }

    var showModePicker by remember { mutableStateOf(false) }
    if (showModePicker) {
        ModePickerSheet(
            selected = state.profile,
            customServerConfigured = customServerConfigured,
            onSelect = {
                viewModel.setProfile(it)
                showModePicker = false
            },
            onDismiss = { showModePicker = false },
        )
    }
    var showDiscard by remember { mutableStateOf(false) }
    if (showDiscard) {
        DiscardRouteDialog(
            onDiscard = {
                showDiscard = false
                viewModel.clearWaypoints()
                viewModel.setBuilderMode(false)
            },
            onDismiss = { showDiscard = false },
        )
    }
    // The trash pill: same question, but the builder stays open afterwards.
    var showClearAll by remember { mutableStateOf(false) }
    if (showClearAll) {
        DiscardRouteDialog(
            onDiscard = {
                showClearAll = false
                viewModel.clearWaypoints()
            },
            onDismiss = { showClearAll = false },
        )
    }
    var waitEditIndex by remember { mutableStateOf<Int?>(null) }
    if (selectedWaypoint != null && state.waypoints.getOrNull(selectedWaypoint!!) == null) {
        // The list changed under the selection (undo/clear) — drop it.
        viewModel.interaction.select(null)
    }
    waitEditIndex?.let { index ->
        WaypointWaitDialog(
            initialSeconds = state.waypoints.getOrNull(index)?.waitSeconds ?: 0,
            onConfirm = { seconds ->
                viewModel.setWaypointWait(index, seconds)
                // Also lands on the running engine mid-drive (a no-op otherwise).
                sessionViewModel.setWaypointWait(index, seconds)
                waitEditIndex = null
            },
            onDismiss = { waitEditIndex = null },
        )
    }
    if (showSaveDialog) {
        SaveRouteDialog(
            suggestedName = saveName,
            onConfirm = { name ->
                viewModel.saveRoute(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    val scaffoldState = rememberBottomSheetScaffoldState()
    val sheetState = scaffoldState.bottomSheetState
    val snackbarHostState = scaffoldState.snackbarHostState
    val dismissLabel = stringResource(R.string.snack_dismiss)
    // Transient feedback is a snackbar, never a card in the stack.
    LaunchedEffect(playbackError) {
        val message = playbackError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, actionLabel = dismissLabel)
        sessionViewModel.consumeError()
    }
    val savedTemplate = stringResource(R.string.snack_saved)
    LaunchedEffect(state.savedName) {
        val name = state.savedName ?: return@LaunchedEffect
        // Consume first, but show from the screen's scope: consuming restarts
        // this effect, which used to cancel the snackbar before it appeared.
        viewModel.consumeSavedName()
        scope.launch { snackbarHostState.showSnackbar(savedTemplate.format(name)) }
    }
    ReleaseSnackbar(session = session, snackbarHostState = snackbarHostState)
    val expanded = sheetState.currentValue == SheetValue.Expanded
    var peekHeightPx by remember { mutableIntStateOf(0) }
    // Until the peek block has been measured (first frame, and again after a
    // configuration change) fall back to a plausible height: snapping to a
    // zero-height anchor parks the sheet off-screen for good.
    val peekMeasured = peekHeightPx > 0
    val peekHeight = if (peekMeasured) {
        with(LocalDensity.current) { peekHeightPx.toDp() }
    } else {
        PEEK_FALLBACK_HEIGHT
    }
    // Focal moment: Play drops the sheet to its peek so the map leads.
    LaunchedEffect(playing, peekMeasured) {
        if (playing && peekMeasured) sheetState.partialExpand()
    }

    // The strip never shows raw coordinates: while a hold's name resolves,
    // stripFor returns null and the previous line stays on screen.
    val arrived = rememberArrived(session = session, playbackState = playbackState)
    val nextStrip = stripFor(
        state = state,
        playbackState = playbackState,
        holding = holding,
        playing = playing,
        builder = builderMode,
        setupReady = setupStatus?.readyToMock != false,
        movingStop = movingWaypoint?.let { stopName(it, state.waypoints.size) },
        arrived = arrived,
    )
    var lastStrip by remember { mutableStateOf(nextStrip ?: StripModel("", StripTone.Neutral, hidden = true)) }
    val strip = nextStrip ?: lastStrip
    SideEffect { if (nextStrip != null) lastStrip = nextStrip }
    // The card animates in and out; while it does, it must keep showing the
    // last *visible* state — never the hidden idle prompt (the "Plan a drive"
    // flash after a hold, session 18).
    var lastVisibleStrip by remember { mutableStateOf(strip) }
    val shownStrip = if (strip.hidden) lastVisibleStrip else strip
    SideEffect { if (!strip.hidden) lastVisibleStrip = strip }
    var cardHeightPx by remember { mutableIntStateOf(0) }
    // A hidden card (idle prompts) takes no room: the tools row drops to the sheet.
    val cardHeight = if (strip.hidden) 0.dp else with(LocalDensity.current) { cardHeightPx.toDp() }
    // The map fits routes above this stack, so a stop never lands under a pill (session 21).
    val toolsHeight = if (builderMode && !playing) Tokens.pillSize + Tokens.mapEdge else 0.dp
    val cardBlock = if (strip.hidden) 0.dp else cardHeight + Tokens.mapEdge
    val overlayBottom = peekHeight + Tokens.mapEdge + cardBlock + toolsHeight
    val overlayBottomPx = with(LocalDensity.current) { overlayBottom.roundToPx() }
    LaunchedEffect(overlayBottomPx) { viewModel.interaction.setOverlayBottom(overlayBottomPx) }

    // The card rides the sheet: as the sheet expands (options, stops) the card
    // lifts with it, so the trio stays readable and a hold's Stop is never
    // buried. Capped so it stops short of the top chrome. Read in the layout
    // phase (offset lambda), never composed.
    var scaffoldHeightPx by remember { mutableIntStateOf(0) }
    var topChromeBottomPx by remember { mutableIntStateOf(0) }
    val mapEdgePx = with(LocalDensity.current) { Tokens.mapEdge.roundToPx() }
    fun sheetLiftPx(): Int {
        val sheetTop = runCatching { sheetState.requireOffset() }.getOrNull() ?: return 0
        val peekTop = scaffoldHeightPx - peekHeightPx
        val lift = (peekTop - sheetTop).roundToInt().coerceAtLeast(0)
        val cardTopAtPeek = peekTop - mapEdgePx - cardHeightPx
        val maxLift = (cardTopAtPeek - topChromeBottomPx - mapEdgePx).coerceAtLeast(0)
        return lift.coerceAtMost(maxLift)
    }

    BottomSheetScaffold(
        modifier = Modifier.onSizeChanged { scaffoldHeightPx = it.height },
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        sheetMaxWidth = Tokens.sheetMaxWidth,
        sheetShape = Tokens.sheetShape,
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetShadowElevation = Tokens.sheetElevation,
        sheetDragHandle = null,
        // Inert while driving: the peek is the whole sheet (Pause / Resume +
        // Finish), so a swipe must not lift it into an empty band.
        sheetSwipeEnabled = !playing,
        containerColor = Color.Transparent,
        // Above the floating card, never on it: a confirmation must not garble the trio.
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = cardBlock + toolsHeight),
            )
        },
        sheetContent = {
            fun toggleSheet() {
                // Using the handle proves the point the hint makes.
                if (sheetHintPending) optionsViewModel.markSheetHintSeen()
                scope.launch { if (expanded) sheetState.partialExpand() else sheetState.expand() }
            }
            // clipToBounds: the always-composed detail column must never bleed
            // past the sheet's rounded top while the peek re-anchors.
            Column(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                // The measured peek is everything that must stay visible at rest:
                // the handle, the peek content and (no navigation bar) the system inset.
                Column(
                    modifier = Modifier
                        .onSizeChanged { peekHeightPx = it.height }
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .navigationBarsPadding(),
                ) {
                    // No grab pill while driving: the sheet has nothing to expand
                    // to, so the affordance (and its TalkBack action) would lie.
                    if (!playing) {
                        SheetHandle(
                            expanded = expanded,
                            onToggle = ::toggleSheet,
                            modifier = Modifier.onGloballyPositioned {
                                handleAnchor = it.boundsInWindow().topCenter
                            },
                        )
                    }
                    val peekMode = when {
                        playing -> PeekMode.PLAYING
                        builderMode -> PeekMode.BUILDER
                        else -> PeekMode.RECORD
                    }
                    AnimatedContent(
                        targetState = peekMode,
                        transitionSpec = { fadeThrough() },
                        label = "peek",
                    ) { mode ->
                        when (mode) {
                            PeekMode.PLAYING -> PlaybackControls(
                                paused = playbackState is PlaybackState.Paused,
                                stopping = playbackState is PlaybackState.Stopping,
                                onPause = sessionViewModel::pause,
                                onResume = sessionViewModel::resume,
                                onFinish = {
                                    // Finish is the end of the drive: the route leaves the map too.
                                    sessionViewModel.stopPlayback()
                                    viewModel.clearWaypoints()
                                },
                                modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space2),
                            )
                            PeekMode.BUILDER -> BuilderPeek(
                                state = state,
                                onDone = { viewModel.setBuilderMode(false) },
                                onClose = {
                                    if (state.waypoints.isEmpty()) {
                                        viewModel.setBuilderMode(false)
                                    } else {
                                        showDiscard = true
                                    }
                                },
                            )
                            PeekMode.RECORD -> RecordPeek(
                                state = state,
                                choice = startChoice,
                                locating = locatingStart,
                                onPickMode = { showModePicker = true },
                                onStart = ::playOrAskStart,
                                onEditRoute = { viewModel.setBuilderMode(true) },
                            )
                        }
                    }
                }
                // Always composed: the sheet's Expanded anchor comes from its content
                // height, so an empty detail column left nothing to drag towards and
                // a slow drag did not move the sheet at all (session 15i).
                // The whole expanded sheet stops at 60% of the window: a long stop
                // list scrolls inside it instead of burying the map.
                val maxDetailHeight = with(LocalDensity.current) {
                    (LocalWindowInfo.current.containerSize.height * SHEET_MAX_FRACTION - peekHeightPx)
                        .coerceAtLeast(0f)
                        .toDp()
                }
                // Nothing during playback: swipe is off above, and this block
                // measures zero (no hairline, no padding, no inset) so the
                // Expanded anchor collapses onto the peek (the speed presets
                // moved to the run box's popover).
                // A hairline where the detail list slides under the peek: the
                // scrolled-away rows end at a visible edge, not a hard clip.
                if (!playing) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val detailInsets = if (playing) {
                    Modifier
                } else {
                    Modifier.padding(bottom = Tokens.space4).navigationBarsPadding()
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = maxDetailHeight)
                        .verticalScroll(rememberScrollState())
                        .then(detailInsets),
                ) {
                    if (!playing && builderMode) {
                        BuilderDetails(
                            state = state,
                            selectedWaypoint = selectedWaypoint,
                            stayAtDestination = options.stayAtDestination,
                            listScrollEnabled = expanded,
                            onSelectWaypoint = { viewModel.interaction.select(it, showPopover = false) },
                            onSetWait = { waitEditIndex = it },
                            onClearWait = { viewModel.setWaypointWait(it, 0) },
                            onRemoveStop = viewModel::removeWaypoint,
                            onMoveStop = viewModel.interaction::beginMove,
                        )
                    } else if (!playing) {
                        OptionsList(
                            settings = options,
                            saveState = when {
                                state.route == null || state.routeIsFallback -> SaveRowState.HIDDEN
                                state.routeSaved -> SaveRowState.SAVED
                                else -> SaveRowState.UNSAVED
                            },
                            saving = naming,
                            onSaveRoute = ::beginSave,
                            onHoldAtCentre = ::holdAtCentre,
                            followCamera = followCamera,
                            onFollowChange = viewModel::setFollowCamera,
                            onStayChange = optionsViewModel::setStayAtDestination,
                            onTrafficChange = optionsViewModel::setTrafficSimEnabled,
                            onWobbleChange = optionsViewModel::setJitterEnabled,
                            onOpenRoutes = onOpenRoutes,
                            onOpenSettings = onOpenSettings,
                        )
                    }
                }
            }
        },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(Tokens.mapEdge)
                    .imePadding()
                    .onGloballyPositioned { topChromeBottomPx = it.boundsInParent().bottom.roundToInt() },
            ) {
                AnimatedVisibility(
                    visible = !playing,
                    enter = Motion.topChromeEnter,
                    exit = Motion.topChromeExit,
                ) {
                    MapSearchBar(
                        state = searchState,
                        units = units,
                        onQueryChange = searchViewModel::setQuery,
                        onSearch = searchViewModel::submit,
                        onFocusChange = searchViewModel::setFieldFocused,
                        onResultSelected = {
                            focusManager.clearFocus()
                            searchViewModel.clearResults()
                            searchViewModel.rememberPick(it)
                            viewModel.selectSearchResult(it)
                        },
                        onClearRecents = searchViewModel::clearRecents,
                        onDismiss = {
                            focusManager.clearFocus()
                            searchViewModel.clearResults()
                        },
                    )
                }
                Spacer(Modifier.height(Tokens.space2))
                MapControls(
                    playing = playing,
                    map3d = map3d,
                    followCamera = followCamera,
                    onToggle3d = viewModel::toggleMap3d,
                    onToggleFollow = { viewModel.setFollowCamera(!followCamera) },
                    onLocate = {
                        // Read at click time; collecting latestFix in composition
                        // would recompose the whole overlay on every fix.
                        val fineLocation = Manifest.permission.ACCESS_FINE_LOCATION
                        val mockedPosition = when (val current = sessionViewModel.session.value) {
                            is MockSessionState.Holding -> current.position
                            is MockSessionState.Playing -> sessionViewModel.latestFix.value?.position
                            else -> null
                        }
                        when {
                            mockedPosition != null -> viewModel.panTo(mockedPosition)
                            context.hasPermission(fineLocation) -> viewModel.locateReal()
                            else -> locatePermissionLauncher.launch(fineLocation)
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                )
            }

            // Strava's "run box": floats above the sheet and rides its top edge,
            // in every state — building, ready, driving, holding. With nothing
            // loaded it is strip-only: the idle prompt is the empty card.
            val speedText = formatMultiplier(speedMultiplier)
            val speedDescription = stringResource(R.string.sheet_speed_cd, speedText)
            var speedPopupOpen by remember { mutableStateOf(false) }
            var speedPillAnchor by remember { mutableStateOf(Offset.Zero) }
            LaunchedEffect(playing) { if (!playing) speedPopupOpen = false }
            // While driving the strip's trailing slot is the speed pill, which
            // opens the preset popover above the card; otherwise copy alone.
            val speedPill: (@Composable () -> Unit)? = if (playing) {
                {
                    FilterChip(
                        selected = speedPopupOpen,
                        onClick = { speedPopupOpen = true },
                        label = { Text(stringResource(R.string.sheet_speed_value, speedText)) },
                        modifier = Modifier
                            .onGloballyPositioned { speedPillAnchor = it.boundsInWindow().topCenter }
                            .semantics { contentDescription = speedDescription },
                    )
                }
            } else {
                null
            }
            if (speedPopupOpen && playing && speedPillAnchor != Offset.Zero) {
                SpeedPopover(
                    anchor = speedPillAnchor,
                    speedMultiplier = speedMultiplier,
                    onSpeedChange = sessionViewModel::setSpeedMultiplier,
                    onDismiss = { speedPopupOpen = false },
                )
            }
            val recordCells = recordCells(state, units)
            val stats: (@Composable () -> Unit)? = when {
                playing -> {
                    { PlaybackStats(playbackState, state.route, units, sessionViewModel) }
                }
                recordCells != null -> {
                    { StatTrio(cells = recordCells) }
                }
                else -> null
            }
            // Only "Building a route" (one stop, nothing to count) shows no card at all.
            AnimatedVisibility(
                visible = !strip.hidden,
                enter = Motion.floatingEnter,
                exit = Motion.floatingExit,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = Tokens.sheetMaxWidth)
                    .offset { IntOffset(0, -sheetLiftPx()) }
                    .padding(horizontal = Tokens.mapEdge)
                    .padding(bottom = peekHeight + Tokens.mapEdge),
            ) {
                // The loaded route's trio doubles as its edit-route entry; the
                // in-drive stats stay inert (the drive's shape is fixed).
                val statsClick = if (!playing && recordCells != null) {
                    {
                        viewModel.setBuilderMode(true)
                        if (sheetHintPending) optionsViewModel.markSheetHintSeen()
                        scope.launch { sheetState.expand() }
                        Unit
                    }
                } else {
                    null
                }
                StatCard(
                    strip = shownStrip,
                    stats = stats,
                    onStripAction = when (shownStrip.action) {
                        StripAction.FIX -> onOpenSetup
                        StripAction.RELEASE -> sessionViewModel::release
                        StripAction.CANCEL_MOVE -> viewModel.interaction::cancelMove
                        null -> null
                    },
                    onStatsClick = statsClick,
                    trailing = speedPill,
                    modifier = Modifier.onSizeChanged { cardHeightPx = it.height },
                )
            }
            // First run: point at the handle and say the sheet pulls up.
            val hintAnchor = handleAnchor.takeIf { sheetHintPending && it != Offset.Zero }
            if (hintAnchor != null && !playing && !builderMode) {
                SheetHintPopover(anchor = hintAnchor, onDismiss = optionsViewModel::markSheetHintSeen)
            }
            // Strava's tap-a-point callout rides the selected marker; a pending
            // Move hides it so the strip's instruction is what the user reads.
            val popoverIndex = popoverWaypoint
            val popoverAnchor = markerScreen
            val popoverStop = popoverIndex?.let { state.waypoints.getOrNull(it) }
            val popover = popoverIndex?.let { index ->
                popoverStop?.let { waypoint ->
                    popoverAnchor?.let { anchor -> Triple(index, waypoint, anchor) }
                }
            }
            if (popover != null && movingWaypoint == null) {
                val (index, waypoint, anchor) = popover
                StopPopover(
                    index = index,
                    waypoint = waypoint,
                    count = state.waypoints.size,
                    anchor = anchor,
                    stayAtDestination = options.stayAtDestination,
                    playing = playing,
                    onSetWait = {
                        waitEditIndex = index
                        viewModel.interaction.select(null)
                    },
                    onMove = { viewModel.interaction.beginMove(index) },
                    onDelete = { viewModel.removeWaypoint(index) },
                    onDismiss = { viewModel.interaction.select(null) },
                )
            }
            AnimatedVisibility(
                visible = builderMode && !playing,
                enter = Motion.floatingEnter,
                exit = Motion.floatingExit,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = Tokens.sheetMaxWidth)
                    .offset { IntOffset(0, -sheetLiftPx()) }
                    .padding(bottom = peekHeight + cardHeight + Tokens.mapEdge * 2),
            ) {
                BuilderTools(
                    canClear = state.waypoints.isNotEmpty(),
                    canSave = state.route != null && !state.routeIsFallback,
                    saving = naming,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    canReverse = state.waypoints.size >= 2,
                    onUndo = viewModel::undoWaypoint,
                    onRedo = viewModel::redoWaypoint,
                    onReverse = viewModel::reverseWaypoints,
                    onSave = ::beginSave,
                    onClearAll = { showClearAll = true },
                )
            }
            // Only while holding: the nudge control is chrome that must recede
            // (design brief) rather than sit dimmed on every map state. It also
            // yields to the builder's pills/card and hides behind an expanded
            // sheet instead of floating dead on top of either.
            AnimatedVisibility(
                visible = holding != null && !playing && !builderMode && !expanded,
                enter = Motion.floatingEnter,
                exit = Motion.floatingExit,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(Tokens.mapEdge),
            ) {
                ThumbstickOverlay(
                    enabled = true,
                    onNudge = { bearingDegrees, deflection ->
                        // Read the live hold at nudge time — the composition capture
                        // would go stale as the dot moves.
                        val hold = sessionViewModel.session.value as? MockSessionState.Holding
                        if (hold != null) {
                            val meters = nudgeMeters(
                                deflection = deflection,
                                zoom = viewModel.camera.value?.zoom ?: DEFAULT_NUDGE_ZOOM,
                                latitudeDegrees = hold.position.latitude,
                                dtSeconds = NUDGE_TICK_MILLIS / MILLIS_PER_SECOND,
                            )
                            if (meters > 0.0) {
                                sessionViewModel.nudgeHold(
                                    GeoMath.destination(hold.position, bearingDegrees, meters),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Record layout peek: the action row (the stat card floats above the sheet). */
@Composable
private fun RecordPeek(
    state: MapViewModel.UiState,
    choice: StartChoice?,
    locating: Boolean,
    onPickMode: () -> Unit,
    onStart: () -> Unit,
    onEditRoute: () -> Unit,
) {
    val route = state.route
    ActionRow(
        profile = state.profile,
        // One stop is startable too: routed from the held spot, or from the
        // device's real position when nothing is mocked.
        canStart = route != null || state.waypoints.size == 1,
        routeLoaded = route != null || state.waypoints.isNotEmpty(),
        onPickMode = onPickMode,
        onStart = onStart,
        onEditRoute = onEditRoute,
        choice = choice,
        locating = locating,
    )
}

/** Expanded builder content: the stops (three rows tall, scrolling inside), then Save. */
@Composable
private fun BuilderDetails(
    state: MapViewModel.UiState,
    selectedWaypoint: Int?,
    stayAtDestination: Boolean,
    listScrollEnabled: Boolean,
    onSelectWaypoint: (Int?) -> Unit,
    onSetWait: (Int) -> Unit,
    onClearWait: (Int) -> Unit,
    onRemoveStop: (Int) -> Unit,
    onMoveStop: (Int) -> Unit,
) {
    if (state.waypoints.isEmpty()) return
    val count = state.waypoints.size
    Text(
        text = stringResource(R.string.sheet_stops_header, count).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space1),
    )
    // Long routes would bury the sheet: about three rows show and the rest scroll
    // inside — only once the sheet is expanded, so a drag up isn't spent on the list.
    // Longer lists end mid-row, fade at the bottom and count the hidden rows, so the
    // cut never reads as "that's all".
    val listState = rememberLazyListState()
    if (selectedWaypoint != null) {
        LaunchedEffect(selectedWaypoint) {
            // Marker taps can pick stop 6: bring its row in, but leave a visible row alone.
            val info = listState.layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { it.index == selectedWaypoint }
            val visible = row != null && row.offset >= 0 && row.offset + row.size <= info.viewportEndOffset
            if (!visible) listState.animateScrollToItem(selectedWaypoint)
        }
    }
    val rows = if (count > STOP_LIST_VISIBLE_ROWS) STOP_LIST_PEEK_ROWS else STOP_LIST_VISIBLE_ROWS.toFloat()
    val cap = Tokens.touchTarget * rows + if (selectedWaypoint != null) Tokens.touchTarget else 0.dp
    val hidden by remember(count) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastFull = info.visibleItemsInfo.lastOrNull { it.offset + it.size <= info.viewportEndOffset }
            if (lastFull == null) 0 else count - lastFull.index - 1
        }
    }
    LazyColumn(
        state = listState,
        userScrollEnabled = listScrollEnabled,
        modifier = Modifier.heightIn(max = cap).bottomFade(visible = hidden > 0, height = Tokens.touchTarget / 2),
    ) {
        itemsIndexed(state.waypoints) { index, waypoint ->
            StopRow(
                index = index,
                waypoint = waypoint,
                count = count,
                selected = index == selectedWaypoint,
                stayAtDestination = stayAtDestination,
                onClick = { onSelectWaypoint(if (index == selectedWaypoint) null else index) },
                onSetWait = { onSetWait(index) },
                onClearWait = { onClearWait(index) },
                onRemove = { onRemoveStop(index) },
                onMove = { onMoveStop(index) },
            )
        }
    }
}

/** One stop: numbered disc, role, wait, and its actions when selected. */
@Composable
private fun StopRow(
    index: Int,
    waypoint: Waypoint,
    count: Int,
    selected: Boolean,
    stayAtDestination: Boolean,
    onClick: () -> Unit,
    onSetWait: () -> Unit,
    onClearWait: () -> Unit,
    onRemove: () -> Unit,
    onMove: () -> Unit,
) {
    // Mirrors the map markers: the first stop is always the start.
    val isStart = index == 0
    val isEnd = index == count - 1 && count >= 2
    val background = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = Tokens.inset),
    ) {
        // Fixed row heights keep "three rows" true for the list cap.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(Tokens.touchTarget)) {
            StopDisc(number = index + 1, isStart = isStart, isEnd = isEnd)
            Spacer(Modifier.width(Tokens.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stopName(index, count), style = MaterialTheme.typography.bodyLarge)
                if (waypoint.waitSeconds > 0) {
                    Text(
                        text = stringResource(
                            R.string.sheet_waits,
                            rememberFormatter().duration(waypoint.waitSeconds.toDouble()),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MockarrTheme.colors.hold,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.sheet_remove_stop))
            }
        }
        if (selected) {
            // On-sheet equivalent of the marker popover (TalkBack path).
            Row(
                horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
                modifier = Modifier.height(Tokens.touchTarget),
            ) {
                val stays = stopStays(index, count, stayAtDestination)
                TextButton(onClick = onSetWait, enabled = !stays) {
                    Text(
                        stringResource(
                            when {
                                stays -> R.string.stop_menu_stays
                                waypoint.waitSeconds > 0 -> R.string.sheet_edit_wait
                                else -> R.string.sheet_set_wait
                            },
                        ),
                    )
                }
                if (waypoint.waitSeconds > 0) {
                    TextButton(onClick = onClearWait) { Text(stringResource(R.string.sheet_remove_wait)) }
                }
                TextButton(onClick = onMove) { Text(stringResource(R.string.stop_menu_move)) }
            }
        }
    }
}

/** The FAB stack on the map's right edge: 3D toggle, then locate / follow. */
@Composable
private fun MapControls(
    playing: Boolean,
    map3d: Boolean,
    followCamera: Boolean,
    onToggle3d: () -> Unit,
    onToggleFollow: () -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Tokens.space2),
        horizontalAlignment = Alignment.End,
        modifier = modifier,
    ) {
        AnimatedVisibility(visible = !playing, enter = Motion.floatingEnter, exit = Motion.floatingExit) {
            MapPill(
                onClick = onToggle3d,
                contentDescription = stringResource(if (map3d) R.string.map_2d_cd else R.string.map_3d_cd),
            ) {
                Text(
                    text = stringResource(if (map3d) R.string.map_2d else R.string.map_3d),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Crossfade(targetState = playing, label = "locateOrFollow") { isPlaying ->
            if (isPlaying) {
                MapIconPill(
                    painter = painterResource(R.drawable.ic_target),
                    contentDescription = stringResource(
                        if (followCamera) R.string.map_following_cd else R.string.map_follow_cd,
                    ),
                    onClick = onToggleFollow,
                    selected = followCamera,
                )
            } else {
                MapIconPill(
                    painter = painterResource(R.drawable.ic_target),
                    contentDescription = stringResource(R.string.map_locate_cd),
                    onClick = onLocate,
                )
            }
        }
    }
}

private enum class PeekMode { RECORD, BUILDER, PLAYING }

/** True when [origin] is so close to [routeStart] that offering a choice would be noise. */
internal fun startsNear(routeStart: LatLng, origin: LatLng): Boolean =
    GeoMath.distanceMeters(routeStart, origin) <= START_FROM_HOLD_METERS

private const val START_FROM_HOLD_METERS = 30.0
private const val SHEET_MAX_FRACTION = 0.6f
private const val STOP_LIST_VISIBLE_ROWS = 3
private const val STOP_LIST_PEEK_ROWS = 3.5f
private const val DEFAULT_NUDGE_ZOOM = 15.0
private const val MILLIS_PER_SECOND = 1_000.0
private val PEEK_FALLBACK_HEIGHT = 200.dp

@Composable
internal fun formatSpeed(metersPerSecond: Double, units: DistanceUnits): String = when (units) {
    DistanceUnits.KILOMETERS ->
        stringResource(R.string.speed_kmh, (metersPerSecond * KMH_PER_MPS).roundToInt())
    DistanceUnits.MILES ->
        stringResource(R.string.speed_mph, (metersPerSecond * MPH_PER_MPS).roundToInt())
}

private const val KMH_PER_MPS = 3.6
private const val MPH_PER_MPS = 2.236936
