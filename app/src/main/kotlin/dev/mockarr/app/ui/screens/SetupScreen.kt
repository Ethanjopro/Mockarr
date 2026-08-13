package dev.mockarr.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

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
                "Two one-time steps are required; the rest improve reliability.",
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
        Spacer(Modifier.height(16.dp))
        SetupCheckCard(
            title = "Notifications allowed",
            subtitle = "Recommended — shows playback progress and controls",
            done = status?.notificationsEnabled == true,
            instructions = "Playback runs as a foreground service; its notification " +
                "lets you pause or stop from anywhere.",
            actionLabel = "Allow notifications",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        SetupCheckCard(
            title = "Battery optimization exemption",
            subtitle = "Recommended — keeps long playbacks alive on aggressive devices",
            done = status?.batteryOptimizationExempt == true,
            instructions = "Find Mockarr in the list and choose \"Don't optimize\". " +
                "Some manufacturers hide this — see dontkillmyapp.com for your device.",
            actionLabel = "Open battery settings",
            onAction = { context.openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
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
    subtitle: String? = null,
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
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
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
