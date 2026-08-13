package dev.mockarr.app.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SetupScreen(
    onBack: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "Setup checklist",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Mockarr uses Android's built-in mock location testing feature. " +
                "Two one-time steps are needed before mocking works.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))

        SetupCheckCard(
            title = "Developer options enabled",
            done = status?.developerOptionsEnabled == true,
            instructions = "Settings → About phone → tap \"Build number\" seven times.",
            actionLabel = "Open About phone",
            onAction = { context.openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS) },
        )
        Spacer(Modifier.height(16.dp))
        SetupCheckCard(
            title = "Mockarr selected as mock location app",
            done = status?.selectedAsMockLocationApp == true,
            instructions = "Developer options → scroll to \"Select mock location app\" → choose Mockarr.",
            actionLabel = "Open developer options",
            onAction = { context.openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
        )

        Spacer(Modifier.height(24.dp))
        if (status?.readyToMock == true) {
            Text(
                text = "All set — Mockarr can mock your location.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
        }
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
    }
}

@Composable
private fun SetupCheckCard(
    title: String,
    done: Boolean,
    instructions: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (done) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = if (done) "Done" else "Not done",
                    tint = if (done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!done) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = instructions,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** OEM settings screens vary; fall back to the top-level Settings app. */
private fun Context.openSettings(action: String) {
    try {
        startActivity(Intent(action))
    } catch (_: RuntimeException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
