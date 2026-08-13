package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import dev.mockarr.core.model.RoutingProfile

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()

    var osrmField by remember(settings.osrmBaseUrl) { mutableStateOf(settings.osrmBaseUrl) }
    var tileField by remember(settings.tileStyleUrl) { mutableStateOf(settings.tileStyleUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Text("Routing server", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "The public OSRM demo server supports driving only. " +
                "Point Mockarr at your own OSRM server to unlock walking and cycling.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = osrmField,
            onValueChange = { osrmField = it },
            label = { Text("OSRM base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { viewModel.applyOsrmBaseUrl(osrmField) },
                enabled = osrmField.trim().trimEnd('/') != settings.osrmBaseUrl,
            ) { Text("Apply") }
            OutlinedButton(
                onClick = { viewModel.testConnection(osrmField) },
                enabled = testState != SettingsViewModel.TestState.Testing,
            ) { Text("Test connection") }
        }
        TestStateLabel(testState)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Map tiles", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tileField,
            onValueChange = { tileField = it },
            label = { Text("Style URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.applyTileStyleUrl(tileField) },
            enabled = tileField.trim() != settings.tileStyleUrl,
        ) { Text("Apply") }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Playback realism", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Update rate: %.1f Hz".format(settings.tickHz),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = settings.tickHz.toFloat(),
            onValueChange = { viewModel.setTickHz(it.toDouble()) },
            valueRange = 0.5f..5f,
            steps = 8,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GPS jitter", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.jitterEnabled,
                onCheckedChange = viewModel::setJitterEnabled,
            )
        }
        if (settings.jitterEnabled) {
            Text(
                "Jitter σ: %.1f m".format(settings.jitterSigmaMeters),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = settings.jitterSigmaMeters.toFloat(),
                onValueChange = { viewModel.setJitterSigmaMeters(it.toDouble()) },
                valueRange = 0.5f..10f,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Default profile", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoutingProfile.entries.forEach { profile ->
                FilterChip(
                    selected = settings.defaultProfile == profile,
                    onClick = { viewModel.setDefaultProfile(profile) },
                    label = { Text(profile.label()) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("About", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Mockarr uses Android's built-in mock location testing feature.\n\n" +
                "Map data © OpenStreetMap contributors · Routing by OSRM · " +
                "Map rendering by MapLibre · Tiles by OpenFreeMap.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TestStateLabel(testState: SettingsViewModel.TestState) {
    when (testState) {
        SettingsViewModel.TestState.Idle -> Unit
        SettingsViewModel.TestState.Testing -> {
            Spacer(Modifier.height(4.dp))
            Text("Testing…", style = MaterialTheme.typography.bodySmall)
        }
        SettingsViewModel.TestState.Success -> {
            Spacer(Modifier.height(4.dp))
            Text(
                "✓ Server responded with a route",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        is SettingsViewModel.TestState.Failure -> {
            Spacer(Modifier.height(4.dp))
            Text(
                "✗ ${testState.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun RoutingProfile.label(): String = when (this) {
    RoutingProfile.DRIVING -> "Driving"
    RoutingProfile.WALKING -> "Walking"
    RoutingProfile.CYCLING -> "Cycling"
}
