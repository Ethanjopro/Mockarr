package dev.mockarr.app.ui.screens

import android.Manifest
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.R
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.app.ui.Motion
import dev.mockarr.app.ui.Motion.fadeThrough
import dev.mockarr.app.ui.formatDistance
import dev.mockarr.app.ui.formatDurationShort
import dev.mockarr.app.ui.map.ActiveDwell
import dev.mockarr.app.ui.map.MockarrMap
import dev.mockarr.app.ui.map.NUDGE_TICK_MILLIS
import dev.mockarr.app.ui.map.ThumbstickOverlay
import dev.mockarr.app.ui.map.effectiveStyleUrl
import dev.mockarr.app.ui.map.nudgeMeters
import dev.mockarr.app.ui.rememberSystemAnimationsEnabled
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.Waypoint
import dev.mockarr.core.routing.GeocodingResult
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
    val selectedWaypoint by viewModel.selectedWaypoint.collectAsStateWithLifecycle()
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val latestFix by sessionViewModel.latestFix.collectAsStateWithLifecycle()
    val dwell by sessionViewModel.dwell.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val playing = session is MockSessionState.Playing
    var holdAwaitingPermission by remember { mutableStateOf<LatLng?>(null) }
    val holdPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val position = holdAwaitingPermission
        holdAwaitingPermission = null
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true && position != null) {
            sessionViewModel.hold(position)
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

    val displayed = remember(state.waypoints, state.route, state.routeIsFallback) {
        displayWaypoints(state.waypoints, state.route, state.routeIsFallback)
    }
    MockarrMap(
        waypoints = displayed,
        routePoints = state.route?.points.orEmpty(),
        routeIsFallback = state.routeIsFallback,
        onMapTap = {
            focusManager.clearFocus()
            // Dismiss-first: with a stop selected, a map tap deselects it rather
            // than dropping a new stop. Read .value at click time (repo rule).
            if (!playing) {
                when {
                    viewModel.selectedWaypoint.value != null -> viewModel.selectWaypoint(null)
                    // Record layout is inert (Strava); only the builder places stops.
                    viewModel.builderMode.value -> viewModel.addWaypoint(it)
                    else -> Unit
                }
            }
        },
        onWaypointTap = { index ->
            focusManager.clearFocus()
            if (!playing) {
                val toggled = index.takeIf { it != viewModel.selectedWaypoint.value }
                viewModel.selectWaypoint(toggled)
            }
        },
        onMapLongPress = {
            focusManager.clearFocus()
            if (!playing) requestHold(it)
        },
        styleUrl = effectiveStyleUrl(tileStyleUrl, isSystemInDarkTheme()),
        palette = MockarrTheme.colors.map,
        visible = visible,
        loadInitialCamera = viewModel::initialCamera,
        onCameraIdle = viewModel::saveCamera,
        onUserGesture = {
            viewModel.setFollowCamera(false)
            focusManager.clearFocus()
        },
        threeDimensional = map3d,
        selectedWaypoint = selectedWaypoint,
        activeDwell = dwell
            ?.takeIf { it.waypointIndex < state.waypoints.size }
            ?.let { ActiveDwell(it.waypointIndex, it.secondsLeft) },
        cameraCommand = cameraCommand,
        pinPosition = (session as? MockSessionState.Holding)?.position,
        playbackPosition = if (playing) latestFix?.position else null,
        cameraFollow = followCamera && playing,
        animateCamera = rememberSystemAnimationsEnabled(),
        modifier = modifier,
    )
}

/**
 * The Map tab's controls — a transparent overlay above the persistent map.
 * One bottom sheet owns the bottom edge (status strip + peek content, expanding
 * to route options and stops); the search field and FAB stack float at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MapViewModel,
    sessionViewModel: MockSessionViewModel,
    setupViewModel: SetupViewModel,
    searchViewModel: MapSearchViewModel = hiltViewModel(),
    optionsViewModel: MapOptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val builderMode by viewModel.builderMode.collectAsStateWithLifecycle()
    val options by optionsViewModel.settings.collectAsStateWithLifecycle()
    val customServerConfigured by viewModel.customServerConfigured.collectAsStateWithLifecycle()
    val setupStatus by setupViewModel.status.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        setupViewModel.refresh()
        onPauseOrDispose { }
    }
    val units by viewModel.units.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val camera by viewModel.camera.collectAsStateWithLifecycle()
    LaunchedEffect(camera) { camera?.let { searchViewModel.cameraBias = it.target } }
    val map3d by viewModel.map3dEnabled.collectAsStateWithLifecycle()
    val followCamera by viewModel.followCamera.collectAsStateWithLifecycle()
    val suggestedName by viewModel.suggestedName.collectAsStateWithLifecycle()
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val playbackState by sessionViewModel.playbackState.collectAsStateWithLifecycle()
    val playbackError by sessionViewModel.error.collectAsStateWithLifecycle()
    val speedMultiplier by sessionViewModel.speedMultiplier.collectAsStateWithLifecycle()
    val selectedWaypoint by viewModel.selectedWaypoint.collectAsStateWithLifecycle()

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
        }
    }
    val locatePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.locateReal()
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

    // Play while holding: offer to route from the held spot first. All session
    // reads happen AT CLICK TIME via .value — never from composition-captured
    // vals — so the prompt appears no matter which order route and hold were
    // created in (a stale capture previously ate the prompt).
    var startChoiceRoute by remember { mutableStateOf<Route?>(null) }
    var showRouteFromHoldPrompt by remember { mutableStateOf(false) }
    var playWhenRouteReady by remember { mutableStateOf(false) }

    fun currentHold(): MockSessionState.Holding? =
        sessionViewModel.session.value as? MockSessionState.Holding

    fun playOrAskStart() {
        val hold = currentHold()
        val ui = viewModel.uiState.value
        val route = ui.route
        when {
            route != null -> {
                val startsAtHold = hold != null &&
                    GeoMath.distanceMeters(route.points.first(), hold.position) <= START_FROM_HOLD_METERS
                if (hold != null && !startsAtHold) startChoiceRoute = route else requestPlay(route)
            }
            ui.waypoints.size == 1 && hold != null -> showRouteFromHoldPrompt = true
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

    startChoiceRoute?.let { pendingRoute ->
        StartChoiceDialog(
            onStartFromHold = {
                startChoiceRoute = null
                currentHold()?.position?.let(viewModel::prependWaypoint)
                playWhenRouteReady = true
            },
            onPlayAsBuilt = {
                startChoiceRoute = null
                requestPlay(pendingRoute)
            },
            onDismiss = { startChoiceRoute = null },
        )
    }

    if (showRouteFromHoldPrompt) {
        RouteFromHoldDialog(
            onConfirm = {
                showRouteFromHoldPrompt = false
                currentHold()?.position?.let { hold ->
                    viewModel.prependWaypoint(hold)
                    playWhenRouteReady = true
                }
            },
            onDismiss = { showRouteFromHoldPrompt = false },
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
    var waitEditIndex by remember { mutableStateOf<Int?>(null) }
    if (selectedWaypoint != null && state.waypoints.getOrNull(selectedWaypoint!!) == null) {
        // The list changed under the selection (undo/clear) — drop it.
        viewModel.selectWaypoint(null)
    }
    waitEditIndex?.let { index ->
        WaypointWaitDialog(
            initialSeconds = state.waypoints.getOrNull(index)?.waitSeconds ?: 0,
            onConfirm = { seconds ->
                viewModel.setWaypointWait(index, seconds)
                waitEditIndex = null
            },
            onDismiss = { waitEditIndex = null },
        )
    }
    if (showSaveDialog) {
        SaveRouteDialog(
            suggestedName = suggestedName,
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
    LaunchedEffect(state.savedConfirmation) {
        val message = state.savedConfirmation ?: return@LaunchedEffect
        viewModel.consumeSavedConfirmation()
        snackbarHostState.showSnackbar(message)
    }
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
    // Tapping a marker opens its options inside the sheet.
    LaunchedEffect(selectedWaypoint) {
        if (selectedWaypoint != null) sheetState.expand()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        sheetShape = Tokens.sheetShape,
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetDragHandle = null,
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetContent = {
            // The bar hides during playback, so the sheet owns the system
            // navigation inset then; otherwise the bar already covers it.
            val insetModifier = if (playing) Modifier.navigationBarsPadding() else Modifier
            Column(modifier = insetModifier.fillMaxWidth()) {
                Column(modifier = Modifier.onSizeChanged { peekHeightPx = it.height }) {
                    val strip = stripFor(
                        state = state,
                        playbackState = playbackState,
                        holding = holding,
                        playing = playing,
                        builder = builderMode,
                        setupReady = setupStatus?.readyToMock != false,
                    )
                    StatusStrip(
                        text = strip.text,
                        tone = strip.tone,
                        actionLabel = strip.actionLabel,
                        onAction = when (strip.action) {
                            StripAction.FIX -> onOpenSetup
                            StripAction.RELEASE -> sessionViewModel::release
                            null -> null
                        },
                    )
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
                            PeekMode.PLAYING -> PlaybackPeek(
                                playbackState = playbackState,
                                route = state.route,
                                units = units,
                                speedMultiplier = speedMultiplier,
                                speedExpanded = expanded,
                                sessionViewModel = sessionViewModel,
                                onToggleSpeed = {
                                    scope.launch { if (expanded) sheetState.partialExpand() else sheetState.expand() }
                                },
                            )
                            PeekMode.BUILDER -> BuilderPeek(
                                state = state,
                                units = units,
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
                                units = units,
                                holdActive = holding != null,
                                onPickMode = { showModePicker = true },
                                onStart = ::playOrAskStart,
                                onEditRoute = { viewModel.setBuilderMode(true) },
                            )
                        }
                    }
                }
                // Only compose the detail column once the sheet is heading to
                // Expanded: while the peek re-anchors after a mode change, the
                // detail rows would otherwise show through the gap.
                val showDetails = expanded || sheetState.targetValue == SheetValue.Expanded
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = Tokens.space4),
                ) {
                    if (!showDetails) return@Column
                    if (playing) {
                        SpeedChips(
                            speedMultiplier = speedMultiplier,
                            onSpeedChange = sessionViewModel::setSpeedMultiplier,
                            modifier = Modifier.padding(horizontal = Tokens.inset),
                        )
                    } else if (builderMode) {
                        BuilderDetails(
                            state = state,
                            selectedWaypoint = selectedWaypoint,
                            onSelectWaypoint = viewModel::selectWaypoint,
                            onSetWait = { waitEditIndex = it },
                            onClearWait = { viewModel.setWaypointWait(it, 0) },
                            onRemoveStop = viewModel::removeWaypoint,
                            onSave = {
                                viewModel.requestNameSuggestion()
                                showSaveDialog = true
                            },
                        )
                    } else {
                        OptionsList(
                            settings = options,
                            followCamera = followCamera,
                            onFollowChange = viewModel::setFollowCamera,
                            onStayChange = optionsViewModel::setStayAtDestination,
                            onTrafficChange = optionsViewModel::setTrafficSimEnabled,
                            onWobbleChange = optionsViewModel::setJitterEnabled,
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
                    .imePadding(),
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
                        onResultSelected = {
                            focusManager.clearFocus()
                            searchViewModel.clearResults()
                            viewModel.selectSearchResult(it)
                        },
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

            AnimatedVisibility(
                visible = builderMode && !playing,
                enter = Motion.floatingEnter,
                exit = Motion.floatingExit,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Tokens.mapEdge, bottom = peekHeight + Tokens.mapEdge),
            ) {
                BuilderTools(
                    canUndo = state.waypoints.isNotEmpty(),
                    canReverse = state.waypoints.size >= 2,
                    onUndo = viewModel::undoWaypoint,
                    onReverse = viewModel::reverseWaypoints,
                    onAddAtCenter = viewModel::addWaypointAtCamera,
                    onClearAll = viewModel::clearWaypoints,
                )
            }
            // Only while holding: the nudge control is chrome that must recede
            // (design brief) rather than sit dimmed on every map state.
            AnimatedVisibility(
                visible = holding != null && !playing,
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
                            sessionViewModel.nudgeHold(
                                GeoMath.destination(hold.position, bearingDegrees, meters),
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Record layout peek: the stat card (zeros at rest, the loaded route's stats) and the action row. */
@Composable
private fun RecordPeek(
    state: MapViewModel.UiState,
    units: DistanceUnits,
    holdActive: Boolean,
    onPickMode: () -> Unit,
    onStart: () -> Unit,
    onEditRoute: () -> Unit,
) {
    val route = state.route
    val placeholder = stringResource(R.string.stat_placeholder)
    val cells = if (route != null) {
        val seconds = route.durationSeconds * state.trafficFactor + route.waypointWaitsSeconds.sum()
        val minutes = (seconds / SECONDS_PER_MINUTE).roundToInt().coerceAtLeast(1)
        listOf(
            StatCell(stringResource(R.string.stat_distance), formatDistance(route.distanceMeters, units)),
            StatCell(
                stringResource(R.string.stat_duration),
                formatDurationShort(minutes * SECONDS_PER_MINUTE.toDouble()),
            ),
            StatCell(stringResource(R.string.stat_stops), state.waypoints.size.toString()),
        )
    } else {
        listOf(
            StatCell(stringResource(R.string.stat_time), placeholder),
            StatCell(stringResource(R.string.stat_distance), placeholder),
            StatCell(stringResource(R.string.stat_speed), placeholder),
        )
    }
    Column {
        StatTrio(
            cells = cells,
            modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space3),
        )
        ActionRow(
            profile = state.profile,
            canStart = route != null || (state.waypoints.size == 1 && holdActive),
            routeLoaded = route != null || state.waypoints.isNotEmpty(),
            onPickMode = onPickMode,
            onStart = onStart,
            onEditRoute = onEditRoute,
        )
    }
}

/** Expanded builder content: the stops, then Save. */
@Composable
private fun BuilderDetails(
    state: MapViewModel.UiState,
    selectedWaypoint: Int?,
    onSelectWaypoint: (Int?) -> Unit,
    onSetWait: (Int) -> Unit,
    onClearWait: (Int) -> Unit,
    onRemoveStop: (Int) -> Unit,
    onSave: () -> Unit,
) {
    if (state.waypoints.isEmpty()) return
    Text(
        text = stringResource(R.string.sheet_stops_header).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space1),
    )
    state.waypoints.forEachIndexed { index, waypoint ->
        StopRow(
            index = index,
            waypoint = waypoint,
            count = state.waypoints.size,
            selected = index == selectedWaypoint,
            onClick = { onSelectWaypoint(if (index == selectedWaypoint) null else index) },
            onSetWait = { onSetWait(index) },
            onClearWait = { onClearWait(index) },
            onRemove = { onRemoveStop(index) },
        )
    }
    Row(modifier = Modifier.padding(horizontal = Tokens.space3)) {
        TextButton(onClick = onSave, enabled = state.route != null && !state.routeIsFallback) {
            Text(stringResource(R.string.sheet_save))
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
    onClick: () -> Unit,
    onSetWait: () -> Unit,
    onClearWait: () -> Unit,
    onRemove: () -> Unit,
) {
    // Mirrors the map markers: the first stop is always the start.
    val isStart = index == 0
    val isEnd = index == count - 1 && count >= 2
    val background = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = Tokens.inset, vertical = Tokens.space2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Tokens.space8)) {
            StopDisc(number = index + 1, isStart = isStart, isEnd = isEnd)
            Spacer(Modifier.width(Tokens.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isStart -> stringResource(R.string.sheet_start)
                        isEnd -> stringResource(R.string.sheet_destination)
                        else -> stringResource(R.string.sheet_stop_n, index + 1)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (waypoint.waitSeconds > 0) {
                    Text(
                        text = stringResource(
                            R.string.sheet_waits,
                            formatDurationShort(waypoint.waitSeconds.toDouble()),
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
        if (selected && !isEnd) {
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.space2)) {
                TextButton(onClick = onSetWait) {
                    Text(
                        stringResource(
                            if (waypoint.waitSeconds > 0) R.string.sheet_edit_wait else R.string.sheet_set_wait,
                        ),
                    )
                }
                if (waypoint.waitSeconds > 0) {
                    TextButton(onClick = onClearWait) { Text(stringResource(R.string.sheet_remove_wait)) }
                }
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
            val description = stringResource(if (map3d) R.string.map_2d_cd else R.string.map_3d_cd)
            FilledTonalIconButton(
                onClick = onToggle3d,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Text(
                    text = stringResource(if (map3d) R.string.map_2d else R.string.map_3d),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Crossfade(targetState = playing, label = "locateOrFollow") { isPlaying ->
            if (isPlaying) {
                FilledIconToggleButton(checked = followCamera, onCheckedChange = { onToggleFollow() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_target),
                        contentDescription = stringResource(
                            if (followCamera) R.string.map_following_cd else R.string.map_follow_cd,
                        ),
                    )
                }
            } else {
                FilledTonalIconButton(onClick = onLocate) {
                    Icon(
                        painter = painterResource(R.drawable.ic_target),
                        contentDescription = stringResource(R.string.map_locate_cd),
                    )
                }
            }
        }
    }
}

@Composable
private fun MapSearchBar(
    state: MapSearchViewModel.SearchState,
    units: DistanceUnits,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onResultSelected: (GeocodingResult) -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.map_search_hint)) },
            singleLine = true,
            shape = Tokens.controlShape,
            trailingIcon = {
                if (state.searching) {
                    CircularProgressIndicator(modifier = Modifier.size(Tokens.space6), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.map_search_cd))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.results.isNotEmpty() || state.errorMessage != null) {
            Spacer(Modifier.height(Tokens.space1))
            Card(shape = Tokens.cardShape) {
                Column {
                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space3),
                        )
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = RESULTS_MAX_HEIGHT)) {
                        items(state.results) { suggestion ->
                            SearchResultRow(suggestion, units) { onResultSelected(suggestion.result) }
                            HorizontalDivider()
                        }
                    }
                    Row(modifier = Modifier.align(Alignment.End)) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_search_close)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    suggestion: MapSearchViewModel.SearchSuggestion,
    units: DistanceUnits,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Tokens.touchTarget)
            .padding(horizontal = Tokens.inset, vertical = Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = suggestion.result.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        suggestion.distanceMeters?.let { meters ->
            Spacer(Modifier.width(Tokens.space2))
            Text(
                text = formatDistance(meters, units),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class PeekMode { RECORD, BUILDER, PLAYING }

private const val START_FROM_HOLD_METERS = 30.0
private const val DEFAULT_NUDGE_ZOOM = 15.0
private const val MILLIS_PER_SECOND = 1_000.0
private const val SECONDS_PER_MINUTE = 60
private val RESULTS_MAX_HEIGHT = 280.dp
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
