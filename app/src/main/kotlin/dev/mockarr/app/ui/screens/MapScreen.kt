package dev.mockarr.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.playback.HoldSource
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.app.ui.formatDistanceProgress
import dev.mockarr.app.ui.formatRouteTimestamp
import dev.mockarr.app.ui.label
import dev.mockarr.app.ui.map.MockarrMap
import dev.mockarr.app.ui.summaryText
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.progressOrZero
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
        waypoints = state.waypoints,
        routePoints = state.route?.points.orEmpty(),
        routeIsFallback = state.routeIsFallback,
        onMapTap = {
            focusManager.clearFocus()
            if (!playing) viewModel.addWaypoint(it)
        },
        onMapLongPress = {
            focusManager.clearFocus()
            if (!playing) requestHold(it)
        },
        styleUrl = tileStyleUrl,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilledTonalIconButton(onClick = viewModel::toggleMap3d) {
                        Text(
                            text = if (map3d) "2D" else "3D",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
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
                    text = when (holding.source) {
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
            }
        }

        if (showSaveDialog) {
            SaveRouteDialog(
                onConfirm = { name ->
                    viewModel.saveRoute(name)
                    showSaveDialog = false
                },
                onDismiss = { showSaveDialog = false },
            )
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        } else {
            ControlCard(
                state = state,
                units = units,
                customServerConfigured = customServerConfigured,
                onProfileSelected = viewModel::setProfile,
                onUndo = viewModel::undoWaypoint,
                onClear = viewModel::clearWaypoints,
                onOpenSetup = onOpenSetup,
                onPlay = { state.route?.let(::requestPlay) },
                onSave = { showSaveDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        }
    }
}

private val SPEED_MULTIPLIER_RANGE =
    SimulationEngine.MIN_MULTIPLIER.toFloat()..SimulationEngine.MAX_MULTIPLIER.toFloat()

private const val WAYPOINT_HINT = "Tap the map to add stops (2 or more make a route). " +
    "Long-press to hold your location at one spot."

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
    val total = route?.distanceMeters ?: 0.0
    val progressText = formatDistanceProgress(total * progress, total, units)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        stopping -> "Stopping…"
                        paused -> "Paused · $progressText"
                        else -> "Driving · $progressText"
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

@Composable
private fun ControlCard(
    state: MapViewModel.UiState,
    units: DistanceUnits,
    customServerConfigured: Boolean,
    onProfileSelected: (RoutingProfile) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onOpenSetup: () -> Unit,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isRouting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Fetching route…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        text = state.route?.summaryText(units) ?: WAYPOINT_HINT,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay, enabled = state.route != null) {
                    Text("Play")
                }
                TextButton(onClick = onSave, enabled = state.route != null && !state.routeIsFallback) {
                    Text("Save")
                }
                TextButton(onClick = onUndo, enabled = state.waypoints.isNotEmpty()) {
                    Text("Undo")
                }
                TextButton(onClick = onClear, enabled = state.waypoints.isNotEmpty()) {
                    Text("Clear")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenSetup) {
                    Text("Setup", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MapSearchBar(
    state: MapViewModel.SearchState,
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
                    state.results.forEachIndexed { index, result ->
                        if (index > 0) HorizontalDivider()
                        Text(
                            text = result.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultSelected(result) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
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
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultName = remember { "Route " + formatRouteTimestamp(System.currentTimeMillis()) }
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save route") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
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
