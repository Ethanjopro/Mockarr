package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MapScreen(
    onOpenSetup: () -> Unit,
    viewModel: MockPinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Map",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The MapLibre map, waypoints, and route playback land here in M2–M3. " +
                "For now: the M1 walking skeleton pins your location to the Eiffel Tower.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        when (val state = uiState) {
            is MockPinViewModel.UiState.Idle -> {
                Button(onClick = viewModel::startMocking) {
                    Text("Mock here (debug pin)")
                }
            }
            is MockPinViewModel.UiState.Mocking -> {
                Text(
                    text = "Mocking location: Eiffel Tower (48.8584, 2.2945).\n" +
                        "Check the blue dot in Google Maps.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::stopMocking) {
                    Text("Stop mocking")
                }
            }
            is MockPinViewModel.UiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::startMocking) {
                    Text("Try again")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onOpenSetup) {
            Text("Setup checklist")
        }
    }
}
