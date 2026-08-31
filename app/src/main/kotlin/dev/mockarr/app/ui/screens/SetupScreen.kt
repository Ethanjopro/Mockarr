package dev.mockarr.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.R
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens

/**
 * The one-time checklist. A readiness strip says whether mocking works right
 * now; required steps that are missing get an error mark, optional ones a
 * neutral ring — the strip never claims "all set" beside a red cross.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // Pinned bar that tints as the list scrolls under it (M3), so rows never just vanish at a hard edge.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back_cd),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.space3, vertical = Tokens.space2),
            verticalArrangement = Arrangement.spacedBy(Tokens.space3),
        ) {
            val required = listOf(status?.developerOptionsEnabled, status?.selectedAsMockLocationApp)
            val missing = required.count { it != true }
            ReadinessStrip(ready = status?.readyToMock == true, missing = missing)
            Text(
                text = stringResource(R.string.setup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Tokens.space1),
            )
            SetupStep(
                iconRes = R.drawable.ic_developer,
                title = stringResource(R.string.setup_dev_title),
                required = true,
                done = status?.developerOptionsEnabled == true,
                instructions = stringResource(R.string.setup_dev_how),
                actionLabel = stringResource(R.string.setup_dev_action),
                onAction = { context.openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS) },
            )
            SetupStep(
                iconRes = R.drawable.ic_pin_check,
                title = stringResource(R.string.setup_mock_title),
                required = true,
                done = status?.selectedAsMockLocationApp == true,
                instructions = stringResource(R.string.setup_mock_how),
                actionLabel = stringResource(R.string.setup_mock_action),
                onAction = { context.openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
            )
            SetupStep(
                iconRes = R.drawable.ic_notifications,
                title = stringResource(R.string.setup_notif_title),
                subtitle = stringResource(R.string.setup_notif_sub),
                required = false,
                done = status?.notificationsEnabled == true,
                instructions = stringResource(R.string.setup_notif_how),
                actionLabel = stringResource(R.string.setup_notif_action),
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    }
                },
            )
            SetupStep(
                iconRes = R.drawable.ic_battery,
                title = stringResource(R.string.setup_battery_title),
                subtitle = stringResource(R.string.setup_battery_sub),
                required = false,
                done = status?.batteryOptimizationExempt == true,
                instructions = stringResource(R.string.setup_battery_how),
                actionLabel = stringResource(R.string.setup_battery_action),
                onAction = { context.openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
            )
            Spacer(Modifier.height(Tokens.space6))
        }
    }
}

/** The status band: ready (teal) or how many required steps remain (error). */
@Composable
private fun ReadinessStrip(ready: Boolean, missing: Int) {
    val colors = MockarrTheme.colors
    val scheme = MaterialTheme.colorScheme
    val (container, content) = if (ready) {
        colors.readyContainer to colors.onReadyContainer
    } else {
        scheme.errorContainer to scheme.onErrorContainer
    }
    val text = when {
        ready -> stringResource(R.string.setup_ready)
        missing == 1 -> stringResource(R.string.setup_steps_left, missing)
        else -> stringResource(R.string.setup_steps_left_plural, missing)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, Tokens.cardShape)
            .padding(horizontal = Tokens.inset, vertical = Tokens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (ready) R.drawable.ic_check else R.drawable.ic_info),
            contentDescription = null,
            tint = content,
        )
        Spacer(Modifier.width(Tokens.space3))
        Text(text, style = MaterialTheme.typography.titleSmall, color = content)
    }
}

@Composable
private fun SetupStep(
    iconRes: Int,
    title: String,
    required: Boolean,
    done: Boolean,
    instructions: String,
    actionLabel: String,
    onAction: () -> Unit,
    subtitle: String? = null,
) {
    Card(
        shape = Tokens.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Tokens.space4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepMark(done = done, required = required)
                Spacer(Modifier.width(Tokens.space3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle ?: stringResource(
                            if (required) R.string.setup_required else R.string.setup_optional,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!done) {
                Spacer(Modifier.height(Tokens.space3))
                Text(instructions, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Tokens.space3))
                if (required) {
                    Button(onClick = onAction) { Text(actionLabel) }
                } else {
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

/** Done → teal check. Required and missing → error "!". Optional and missing → neutral ring. */
@Composable
private fun StepMark(done: Boolean, required: Boolean) {
    val colors = MockarrTheme.colors
    val scheme = MaterialTheme.colorScheme
    val doneDescription = stringResource(R.string.setup_done_cd)
    when {
        done -> Box(
            modifier = Modifier.size(MARK_SIZE).background(colors.readyContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = doneDescription,
                tint = colors.onReadyContainer,
                modifier = Modifier.size(MARK_ICON),
            )
        }
        required -> Box(
            modifier = Modifier.size(MARK_SIZE).background(scheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = scheme.onErrorContainer,
            )
        }
        else -> Box(
            modifier = Modifier
                .size(MARK_SIZE)
                .border(2.dp, scheme.outlineVariant, CircleShape)
                .background(Color.Transparent, CircleShape),
        )
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

private val MARK_SIZE = Tokens.space8
private val MARK_ICON = 20.dp
