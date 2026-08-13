package dev.mockarr.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.ui.map.MockarrMap
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    onOpenSetup: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
    pinViewModel: MockPinViewModel = hiltViewModel(),
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pinState by pinViewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playbackViewModel.playbackState.collectAsStateWithLifecycle()
    val latestFix by playbackViewModel.latestFix.collectAsStateWithLifecycle()
    val playbackError by playbackViewModel.error.collectAsStateWithLifecycle()
    val speedMultiplier by playbackViewModel.speedMultiplier.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var followCamera by rememberSaveable { mutableStateOf(true) }
    var routeAwaitingPermission by remember { mutableStateOf<Route?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val route = routeAwaitingPermission
        routeAwaitingPermission = null
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true && route != null) {
            playbackViewModel.play(route)
        }
    }

    fun requestPlay(route: Route) {
        val needed = buildList {
            if (!context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isEmpty()) {
            playbackViewModel.play(route)
        } else {
            routeAwaitingPermission = route
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    val sessionActive = playbackState != null

    Box(modifier = Modifier.fillMaxSize()) {
        MockarrMap(
            waypoints = state.waypoints,
            routePoints = state.route?.points.orEmpty(),
            routeIsFallback = state.routeIsFallback,
            onMapTap = { if (!sessionActive) viewModel.addWaypoint(it) },
            onMapLongPress = { if (!sessionActive) pinViewModel.startMocking(it) },
            playbackPosition = if (sessionActive) latestFix?.position else null,
            cameraFollow = followCamera && sessionActive,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            when (val pin = pinState) {
                is MockPinViewModel.UiState.Mocking -> StatusCard(
                    text = "Pin-mocking %.4f, %.4f".format(pin.position.latitude, pin.position.longitude),
                    actionLabel = "Stop",
                    onAction = pinViewModel::stopMocking,
                )
                is MockPinViewModel.UiState.Error -> StatusCard(
                    text = pin.message,
                    actionLabel = "Dismiss",
                    onAction = pinViewModel::dismissError,
                    isError = true,
                )
                MockPinViewModel.UiState.Idle -> Unit
            }
            playbackError?.let { message ->
                Spacer(Modifier.height(8.dp))
                StatusCard(
                    text = message,
                    actionLabel = "Dismiss",
                    onAction = playbackViewModel::consumeError,
                    isError = true,
                )
            }
            state.errorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                StatusCard(text = message, isError = true)
            }
        }

        if (sessionActive) {
            PlaybackCard(
                playbackState = playbackState,
                route = state.route,
                speedMultiplier = speedMultiplier,
                followCamera = followCamera,
                onToggleFollow = { followCamera = !followCamera },
                onSpeedChange = playbackViewModel::setSpeedMultiplier,
                onPause = playbackViewModel::pause,
                onResume = playbackViewModel::resume,
                onStop = playbackViewModel::stopPlayback,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        } else {
            ControlCard(
                state = state,
                onProfileSelected = viewModel::setProfile,
                onClear = viewModel::clearWaypoints,
                onOpenSetup = onOpenSetup,
                onPlay = { state.route?.let(::requestPlay) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        }
    }
}

private fun android.content.Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

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
    speedMultiplier: Double,
    followCamera: Boolean,
    onToggleFollow: () -> Unit,
    onSpeedChange: (Double) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = when (val s = playbackState) {
        is PlaybackState.Playing -> s.progress
        is PlaybackState.Paused -> s.progress
        else -> 0.0
    }
    val paused = playbackState is PlaybackState.Paused
    val stopping = playbackState is PlaybackState.Stopping
    val km = (route?.distanceMeters ?: 0.0) / 1000.0

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        stopping -> "Stopping…"
                        paused -> "Paused · %.1f / %.1f km".format(km * progress, km)
                        else -> "Driving · %.1f / %.1f km".format(km * progress, km)
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
                    valueRange = 0.25f..4f,
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
    onProfileSelected: (RoutingProfile) -> Unit,
    onClear: () -> Unit,
    onOpenSetup: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.profile == RoutingProfile.DRIVING,
                    onClick = { onProfileSelected(RoutingProfile.DRIVING) },
                    label = { Text("Driving") },
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = { Text("Walking") },
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = { Text("Cycling") },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isRouting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Fetching route…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        text = state.route?.summaryText()
                            ?: "Tap the map to add waypoints (2+ for a route). Long-press to pin-mock.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay, enabled = state.route != null) {
                    Text("Play")
                }
                TextButton(onClick = onClear, enabled = state.waypoints.isNotEmpty()) {
                    Text("Clear")
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onOpenSetup) {
                    Text("Setup")
                }
            }
        }
    }
}

private fun Route.summaryText(): String {
    val km = distanceMeters / 1000.0
    val minutes = (durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
    return "%.1f km · about %d min".format(km, minutes)
}
