package dev.mockarr.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.R
import dev.mockarr.app.playback.HoldSource
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.app.ui.formatDistance
import dev.mockarr.app.ui.formatDistanceProgress
import dev.mockarr.app.ui.formatDurationShort
import dev.mockarr.app.ui.formatRouteTimestamp
import dev.mockarr.app.ui.label
import dev.mockarr.app.ui.map.MockarrMap
import dev.mockarr.app.ui.map.effectiveStyleUrl
import dev.mockarr.app.ui.summaryText
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.progressOrZero
import dev.mockarr.core.model.remainingSecondsOrNull
import dev.mockarr.core.routing.GeocodingResult
import dev.mockarr.core.simulation.SimulationEngine

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
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val latestFix by sessionViewModel.latestFix.collectAsStateWithLifecycle()
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

    MockarrMap(
        waypoints = displayWaypoints(state.waypoints, state.route, state.routeIsFallback),
        routePoints = state.route?.points.orEmpty(),
        routeIsFallback = state.routeIsFallback,
        onMapTap = {
            focusManager.clearFocus()
            if (!playing) viewModel.addWaypoint(it)
        },
        onWaypointTap = {
            focusManager.clearFocus()
            if (!playing) viewModel.selectWaypoint(it)
        },
        onMapLongPress = {
            focusManager.clearFocus()
            if (!playing) requestHold(it)
        },
        styleUrl = effectiveStyleUrl(tileStyleUrl, isSystemInDarkTheme()),
        visible = visible,
        loadInitialCamera = viewModel::initialCamera,
        onCameraIdle = viewModel::saveCamera,
        onUserGesture = {
            viewModel.setFollowCamera(false)
            focusManager.clearFocus()
        },
        threeDimensional = map3d,
        cameraCommand = cameraCommand,
        pinPosition = (session as? MockSessionState.Holding)?.position,
        playbackPosition = if (playing) latestFix?.position else null,
        cameraFollow = followCamera && playing,
        modifier = modifier,
    )
}

/** The Map tab's controls — a transparent overlay above the persistent map. */
@Composable
fun MapScreen(
    onOpenSetup: () -> Unit,
    viewModel: MapViewModel,
    sessionViewModel: MockSessionViewModel,
    setupViewModel: SetupViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val customServerConfigured by viewModel.customServerConfigured.collectAsStateWithLifecycle()
    val setupStatus by setupViewModel.status.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        setupViewModel.refresh()
        onPauseOrDispose { }
    }
    val units by viewModel.units.collectAsStateWithLifecycle()
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    val map3d by viewModel.map3dEnabled.collectAsStateWithLifecycle()
    val followCamera by viewModel.followCamera.collectAsStateWithLifecycle()
    val suggestedName by viewModel.suggestedName.collectAsStateWithLifecycle()
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val playbackState by sessionViewModel.playbackState.collectAsStateWithLifecycle()
    val playbackError by sessionViewModel.error.collectAsStateWithLifecycle()
    val speedMultiplier by sessionViewModel.speedMultiplier.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
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
        AlertDialog(
            onDismissRequest = { showRouteFromHoldPrompt = false },
            title = { Text("Route from held location?") },
            text = { Text("Build a route from your held location to this stop and play it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRouteFromHoldPrompt = false
                        currentHold()?.position?.let { hold ->
                            viewModel.prependWaypoint(hold)
                            playWhenRouteReady = true
                        }
                    },
                ) { Text("Route and play") }
            },
            dismissButton = {
                TextButton(onClick = { showRouteFromHoldPrompt = false }) { Text("Cancel") }
            },
        )
    }

    val selectedWaypoint by viewModel.selectedWaypoint.collectAsStateWithLifecycle()
    var waitEditIndex by remember { mutableStateOf<Int?>(null) }

    selectedWaypoint?.let { index ->
        val waypoint = state.waypoints.getOrNull(index)
        if (waypoint == null) {
            // The list changed under the open menu (undo/clear) — drop it.
            viewModel.selectWaypoint(null)
        } else {
            WaypointOptionsDialog(
                stopNumber = index + 1,
                isDestination = index == state.waypoints.lastIndex && state.waypoints.size >= 2,
                currentWaitSeconds = waypoint.waitSeconds,
                onSetWait = {
                    waitEditIndex = index
                    viewModel.selectWaypoint(null)
                },
                onClearWait = { viewModel.setWaypointWait(index, 0) },
                onDelete = { viewModel.removeWaypoint(index) },
                onDismiss = { viewModel.selectWaypoint(null) },
            )
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            if (!playing) {
                MapSearchBar(
                    state = searchState,
                    units = units,
                    onQueryChange = viewModel::setSearchQuery,
                    onSearch = viewModel::submitSearch,
                    onResultSelected = {
                        focusManager.clearFocus()
                        viewModel.selectSearchResult(it)
                    },
                    onDismiss = {
                        focusManager.clearFocus()
                        viewModel.clearSearchResults()
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (!playing) {
                        FilledTonalIconButton(onClick = viewModel::toggleMap3d) {
                            Text(
                                text = if (map3d) "2D" else "3D",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    val fineLocation = Manifest.permission.ACCESS_FINE_LOCATION
                    FilledTonalIconButton(
                        onClick = {
                            // Read at click time; collecting latestFix in composition
                            // would recompose the whole overlay on every fix.
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
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_target),
                            contentDescription = "Go to my location",
                        )
                    }
                }
            }
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

        // Status banners live at the bottom, stacked directly above the card.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            if (setupStatus?.readyToMock == false && !playing) {
                StatusCard(
                    text = "Mocking isn't set up yet — routes will draw, but playback won't move your location.",
                    actionLabel = "Fix",
                    onAction = onOpenSetup,
                    isError = true,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (holding != null) {
                val coords = "%.4f, %.4f".format(holding.position.latitude, holding.position.longitude)
                StatusCard(
                    text = holding.placeName?.let { "Holding at $it" }
                        ?: when (holding.source) {
                            HoldSource.DESTINATION -> "Holding at destination"
                            HoldSource.PIN -> "Holding your location at $coords"
                        },
                    actionLabel = "Stop",
                    onAction = sessionViewModel::release,
                )
                Spacer(Modifier.height(8.dp))
            }
            playbackError?.let { message ->
                StatusCard(
                    text = message,
                    actionLabel = "Dismiss",
                    onAction = sessionViewModel::consumeError,
                    isError = true,
                )
                Spacer(Modifier.height(8.dp))
            }
            state.errorMessage?.let { message ->
                StatusCard(text = message, isError = true)
                Spacer(Modifier.height(8.dp))
            }
            state.savedConfirmation?.let { message ->
                StatusCard(
                    text = message,
                    actionLabel = "OK",
                    onAction = viewModel::consumeSavedConfirmation,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (playing) {
                PlaybackCard(
                    playbackState = playbackState,
                    route = state.route,
                    units = units,
                    speedMultiplier = speedMultiplier,
                    followCamera = followCamera,
                    onToggleFollow = { viewModel.setFollowCamera(!followCamera) },
                    onSpeedChange = sessionViewModel::setSpeedMultiplier,
                    onPause = sessionViewModel::pause,
                    onResume = sessionViewModel::resume,
                    onStop = sessionViewModel::stopPlayback,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                RouteCreatorCard(
                    state = state,
                    units = units,
                    holdActive = holding != null,
                    customServerConfigured = customServerConfigured,
                    onProfileSelected = viewModel::setProfile,
                    onUndo = viewModel::undoWaypoint,
                    onClear = viewModel::clearWaypoints,
                    onPlay = ::playOrAskStart,
                    onSave = {
                        viewModel.requestNameSuggestion()
                        showSaveDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val SPEED_MULTIPLIER_RANGE =
    SimulationEngine.MIN_MULTIPLIER.toFloat()..SimulationEngine.MAX_MULTIPLIER.toFloat()

private const val WAYPOINT_HINT = "Tap the map to add stops (2 or more make a route). " +
    "Long-press to hold your location at one spot."

private const val HALF_TURN = 180f
private const val START_FROM_HOLD_METERS = 30.0

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Permissions the mock session service needs before it can start. */
private fun Context.missingMockPermissions(): List<String> = buildList {
    if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    ) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun StatusCard(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    isError: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun PlaybackCard(
    playbackState: PlaybackState?,
    route: Route?,
    units: DistanceUnits,
    speedMultiplier: Double,
    followCamera: Boolean,
    onToggleFollow: () -> Unit,
    onSpeedChange: (Double) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = playbackState.progressOrZero
    val paused = playbackState is PlaybackState.Paused
    val stopping = playbackState is PlaybackState.Stopping
    val dwelling = playbackState as? PlaybackState.Dwelling
    val total = route?.distanceMeters ?: 0.0
    val progressText = formatDistanceProgress(total * progress, total, units)
    val etaSuffix = playbackState.remainingSecondsOrNull
        ?.let { " · ${formatDurationShort(it)} left" }
        .orEmpty()

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        stopping -> "Stopping…"
                        paused -> "Paused · $progressText$etaSuffix"
                        dwelling != null ->
                            "Waiting ${formatDurationShort(dwelling.waitSecondsLeft)} · " +
                                "$progressText$etaSuffix"
                        else -> "Driving · $progressText$etaSuffix"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onToggleFollow) {
                    Text(if (followCamera) "Following" else "Follow")
                }
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speed ×%.2g".format(speedMultiplier), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = speedMultiplier.toFloat(),
                    onValueChange = { onSpeedChange(it.toDouble()) },
                    valueRange = SPEED_MULTIPLIER_RANGE,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (paused) {
                    Button(onClick = onResume, enabled = !stopping) { Text("Resume") }
                } else {
                    Button(onClick = onPause, enabled = !stopping) { Text("Pause") }
                }
                OutlinedButton(onClick = onStop, enabled = !stopping) { Text("Stop") }
            }
        }
    }
}

/**
 * Compact route bar: summary + Play + a chevron. Expanding reveals the
 * profile chips and route actions — the expanded column is where future
 * per-route options land without crowding the collapsed bar.
 */
@Composable
private fun RouteCreatorCard(
    state: MapViewModel.UiState,
    units: DistanceUnits,
    holdActive: Boolean,
    customServerConfigured: Boolean,
    onProfileSelected: (RoutingProfile) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var lastCount by remember { mutableIntStateOf(state.waypoints.size) }
    // Auto-expand when route building starts; collapse again on Clear.
    LaunchedEffect(state.waypoints.size) {
        val count = state.waypoints.size
        if (lastCount == 0 && count > 0) expanded = true
        if (count == 0) expanded = false
        lastCount = count
    }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isRouting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                val route = state.route
                Text(
                    text = when {
                        state.isRouting -> "Fetching route…"
                        route != null -> route.summaryText(units, state.trafficFactor)
                        state.waypoints.size == 1 && holdActive -> "Play to route from your held spot"
                        state.waypoints.isNotEmpty() -> "Add another stop to make a route"
                        else -> "Tap the map to add stops"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (route != null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onPlay,
                    enabled = route != null || (state.waypoints.size == 1 && holdActive),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                }
                val rotation by animateFloatAsState(if (expanded) HALF_TURN else 0f, label = "chevron")
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Hide route options" else "Show route options",
                        modifier = Modifier.rotate(rotation),
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    if (state.route == null) {
                        Text(
                            text = WAYPOINT_HINT,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    // The public routing server only supports driving; the profile
                    // picker appears once a custom server is configured.
                    if (customServerConfigured) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoutingProfile.entries.forEach { profile ->
                                FilterChip(
                                    selected = state.profile == profile,
                                    onClick = { onProfileSelected(profile) },
                                    label = { Text(profile.label()) },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onSave,
                            enabled = state.route != null && !state.routeIsFallback,
                        ) { Text("Save") }
                        TextButton(onClick = onUndo, enabled = state.waypoints.isNotEmpty()) {
                            Text("Undo")
                        }
                        TextButton(onClick = onClear, enabled = state.waypoints.isNotEmpty()) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapSearchBar(
    state: MapViewModel.SearchState,
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
            placeholder = { Text("Search for a place") },
            singleLine = true,
            trailingIcon = {
                if (state.searching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.results.isNotEmpty() || state.errorMessage != null) {
            Spacer(Modifier.height(4.dp))
            Card {
                Column {
                    state.errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    state.results.forEachIndexed { index, suggestion ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultSelected(suggestion.result) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = suggestion.result.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                modifier = Modifier.weight(1f),
                            )
                            suggestion.distanceMeters?.let { meters ->
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = formatDistance(meters, units),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.align(Alignment.End)) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveRouteDialog(
    suggestedName: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultName = remember { "Route " + formatRouteTimestamp(System.currentTimeMillis()) }
    var name by remember { mutableStateOf(suggestedName ?: defaultName) }
    var edited by remember { mutableStateOf(false) }
    // The reverse-geocoded suggestion may arrive after the dialog opens; adopt
    // it only while the user hasn't typed anything.
    LaunchedEffect(suggestedName) {
        if (!edited && suggestedName != null) name = suggestedName
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save route") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    edited = true
                },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
