package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.core.data.MockarrSettings
import dev.mockarr.core.model.DistanceUnits
import kotlin.math.exp
import kotlin.math.ln

@Composable
fun SettingsScreen(
    onOpenSetup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        FilledTonalButton(
            onClick = onOpenSetup,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Mock location setup", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        }
        SectionBreak()

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

        Text("Playback", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            label = "Stay at destination",
            description = "When a route ends, keep your location at the endpoint until you " +
                "press Stop. When off, Mockarr returns to a held pin if you set one, " +
                "otherwise to your real location.",
            checked = settings.stayAtDestination,
            onCheckedChange = viewModel::setStayAtDestination,
        )
        SwitchRow(
            label = "Rush-hour traffic",
            description = "Slows playback and lengthens time estimates during peak commute " +
                "hours, like real traffic would.",
            checked = settings.trafficSimEnabled,
            onCheckedChange = viewModel::setTrafficSimEnabled,
        )
        // Sliders hold drag state locally and persist once per gesture — a DataStore
        // write per drag event would be a full file rewrite each time. The tick
        // slider is log-scaled so most of its travel covers the low (realistic) end.
        var tickHzDrag by remember(settings.tickHz) { mutableStateOf(settings.tickHz) }
        val tickDescription = "How often Mockarr publishes a new position. " +
            "Real phones report about once per second."
        SettingLabel(
            text = "Updates per second: %.1f".format(tickHzDrag),
            description = tickDescription,
        )
        Slider(
            value = tickHzToSlider(tickHzDrag),
            onValueChange = { tickHzDrag = sliderToTickHz(it) },
            onValueChangeFinished = { viewModel.setTickHz(tickHzDrag) },
            valueRange = 0f..1f,
            modifier = Modifier.semantics {
                contentDescription = "Updates per second. $tickDescription"
                stateDescription = "%.1f per second".format(tickHzDrag)
            },
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            label = "Progress alert",
            description = "Sends a notification when a playing route reaches the " +
                "percentage you choose below.",
            checked = settings.progressAlertEnabled,
            onCheckedChange = viewModel::setProgressAlertEnabled,
        )
        if (settings.progressAlertEnabled) {
            var percentDrag by remember(settings.progressAlertPercent) {
                mutableStateOf(settings.progressAlertPercent.toFloat())
            }
            val alertDescription =
                "How far through the route playback must be before the alert fires."
            SettingLabel(
                text = "Alert at: ${percentDrag.toInt()}%",
                description = alertDescription,
            )
            Slider(
                value = percentDrag,
                onValueChange = { percentDrag = it },
                onValueChangeFinished = { viewModel.setProgressAlertPercent(percentDrag.toInt()) },
                valueRange = PROGRESS_ALERT_RANGE,
                steps = PROGRESS_ALERT_STEPS,
                modifier = Modifier.semantics {
                    contentDescription = "Alert at percentage. $alertDescription"
                    stateDescription = "${percentDrag.toInt()} percent"
                },
            )
        }
        SwitchRow(
            label = "Realistic GPS wobble",
            description = "Adds tiny random offsets so positions look like real GPS " +
                "instead of a perfect line.",
            checked = settings.jitterEnabled,
            onCheckedChange = viewModel::setJitterEnabled,
        )
        if (settings.jitterEnabled) {
            var sigmaDrag by remember(settings.jitterSigmaMeters) {
                mutableStateOf(settings.jitterSigmaMeters.toFloat())
            }
            val sigmaDescription =
                "How far the random GPS offsets can wander from the true position."
            SettingLabel(
                text = "Wobble amount: %.1f m".format(sigmaDrag),
                description = sigmaDescription,
            )
            Slider(
                value = sigmaDrag,
                onValueChange = { sigmaDrag = it },
                onValueChangeFinished = { viewModel.setJitterSigmaMeters(sigmaDrag.toDouble()) },
                valueRange = JITTER_SIGMA_RANGE,
                modifier = Modifier.semantics {
                    contentDescription = "Wobble amount. $sigmaDescription"
                    stateDescription = "%.1f meters".format(sigmaDrag)
                },
            )
        }
        SectionBreak()

        Text("About", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Mockarr uses Android's built-in mock location testing feature. " +
                "Hold your finger on a setting's name for details.\n\n" +
                "Map data © OpenStreetMap contributors · Routing by OSRM · " +
                "Search by Photon (komoot) · Elevation by Open-Meteo · " +
                "Map rendering by MapLibre · Tiles by OpenFreeMap.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * A switch setting: terse label (long-press for the description) + toggle. The
 * whole row is one toggle target, merged for TalkBack with the tooltip text in
 * its announcement — the long-press tooltip alone is invisible to screen readers.
 */
@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        // In a uiautomator dump the description shows on an inert child node,
        // not the checkable row — that is how Compose represents merged
        // semantics; TalkBack reads the whole merged subtree on row focus.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = "$label. $description" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Weight goes on a Spacer: TooltipBox swallows a weight passed via its
        // own modifier, which left the switch hugging the label mid-screen.
        SettingLabel(text = label, description = description)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Long-press the label to see [description] in a tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingLabel(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionBreak() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
}

private val JITTER_SIGMA_RANGE =
    MockarrSettings.JITTER_SIGMA_MIN.toFloat()..MockarrSettings.JITTER_SIGMA_MAX.toFloat()

private val PROGRESS_ALERT_RANGE =
    MockarrSettings.PROGRESS_ALERT_MIN.toFloat()..MockarrSettings.PROGRESS_ALERT_MAX.toFloat()

// 5%-granularity detents across the 10-95 range.
private val PROGRESS_ALERT_STEPS =
    (MockarrSettings.PROGRESS_ALERT_MAX - MockarrSettings.PROGRESS_ALERT_MIN) / 5 - 1

private val LN_TICK_MIN = ln(MockarrSettings.TICK_HZ_MIN)
private val LN_TICK_MAX = ln(MockarrSettings.TICK_HZ_MAX)

private fun tickHzToSlider(hz: Double): Float {
    val clamped = hz.coerceIn(MockarrSettings.TICK_HZ_MIN, MockarrSettings.TICK_HZ_MAX)
    return ((ln(clamped) - LN_TICK_MIN) / (LN_TICK_MAX - LN_TICK_MIN)).toFloat()
}

private fun sliderToTickHz(t: Float): Double = exp(LN_TICK_MIN + t * (LN_TICK_MAX - LN_TICK_MIN))
