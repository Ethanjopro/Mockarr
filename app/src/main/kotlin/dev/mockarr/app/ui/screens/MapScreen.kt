package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.ui.map.MockarrMap
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import kotlin.math.roundToInt

@Composable
fun MapScreen(
    onOpenSetup: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
    pinViewModel: MockPinViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pinState by pinViewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        MockarrMap(
            waypoints = state.waypoints,
            routePoints = state.route?.points.orEmpty(),
            routeIsFallback = state.routeIsFallback,
            onMapTap = viewModel::addWaypoint,
            onMapLongPress = pinViewModel::startMocking,
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
            state.errorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                StatusCard(text = message, isError = true)
            }
        }

        ControlCard(
            state = state,
            onProfileSelected = viewModel::setProfile,
            onClear = viewModel::clearWaypoints,
            onOpenSetup = onOpenSetup,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
        )
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
private fun ControlCard(
    state: MapViewModel.UiState,
    onProfileSelected: (RoutingProfile) -> Unit,
    onClear: () -> Unit,
    onOpenSetup: () -> Unit,
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
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
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
                Button(onClick = {}, enabled = false) {
                    Text("Play (M3)")
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
