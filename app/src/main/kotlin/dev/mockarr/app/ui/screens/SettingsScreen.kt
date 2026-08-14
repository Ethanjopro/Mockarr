package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.core.data.MockarrSettings
import dev.mockarr.core.model.DistanceUnits

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Text("Distance units", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.units == DistanceUnits.MILES,
                onClick = { viewModel.setUnits(DistanceUnits.MILES) },
                label = { Text("Miles", style = MaterialTheme.typography.bodyLarge) },
            )
            FilterChip(
                selected = settings.units == DistanceUnits.KILOMETERS,
                onClick = { viewModel.setUnits(DistanceUnits.KILOMETERS) },
                label = { Text("Kilometers", style = MaterialTheme.typography.bodyLarge) },
            )
        }
        SectionBreak()

        Text("After a route ends", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Stay at destination", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Keep your location at the endpoint until you press Stop. " +
                        "When off, Mockarr returns to a held pin if you set one, " +
                        "otherwise to your real location.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = settings.stayAtDestination,
                onCheckedChange = viewModel::setStayAtDestination,
            )
        }
        SectionBreak()

        Text("Location updates", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "How the mocked location behaves while a route plays.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        // Sliders hold drag state locally and persist once per gesture — a DataStore
        // write per drag event would be a full file rewrite each time.
        var tickHzDrag by remember(settings.tickHz) { mutableStateOf(settings.tickHz.toFloat()) }
        Text(
            "Updates per second: %.1f".format(tickHzDrag),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "How often Mockarr publishes a new position. Real phones report about once per second.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = tickHzDrag,
            onValueChange = { tickHzDrag = it },
            onValueChangeFinished = { viewModel.setTickHz(tickHzDrag.toDouble()) },
            valueRange = TICK_HZ_RANGE,
            steps = 8,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Realistic GPS wobble", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Adds tiny random offsets so positions look like real GPS instead of a perfect line.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = settings.jitterEnabled,
                onCheckedChange = viewModel::setJitterEnabled,
            )
        }
        if (settings.jitterEnabled) {
            var sigmaDrag by remember(settings.jitterSigmaMeters) {
                mutableStateOf(settings.jitterSigmaMeters.toFloat())
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Wobble amount: %.1f m".format(sigmaDrag),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = sigmaDrag,
                onValueChange = { sigmaDrag = it },
                onValueChangeFinished = { viewModel.setJitterSigmaMeters(sigmaDrag.toDouble()) },
                valueRange = JITTER_SIGMA_RANGE,
            )
        }
        SectionBreak()

        Text("About", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Mockarr uses Android's built-in mock location testing feature.\n\n" +
                "Map data © OpenStreetMap contributors · Routing by OSRM · " +
                "Search by Photon (komoot) · Map rendering by MapLibre · Tiles by OpenFreeMap.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionBreak() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
}

private val TICK_HZ_RANGE =
    MockarrSettings.TICK_HZ_MIN.toFloat()..MockarrSettings.TICK_HZ_MAX.toFloat()
private val JITTER_SIGMA_RANGE =
    MockarrSettings.JITTER_SIGMA_MIN.toFloat()..MockarrSettings.JITTER_SIGMA_MAX.toFloat()
