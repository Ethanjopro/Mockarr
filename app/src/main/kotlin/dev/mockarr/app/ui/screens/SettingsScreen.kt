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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.R
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.data.MockarrSettings
import dev.mockarr.core.model.DistanceUnits
import kotlin.math.exp
import kotlin.math.ln

/**
 * Settings after Strava's: a tile grid for the headline settings, then
 * icon rows grouped under small-caps headers. Every description is visible —
 * no long-press tooltips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    setupViewModel: SetupViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val setupStatus by setupViewModel.status.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        setupViewModel.refresh()
        onPauseOrDispose { }
    }
    var showModePicker by remember { mutableStateOf(false) }
    var showServer by remember { mutableStateOf(false) }
    if (showModePicker) {
        ModePickerSheet(
            selected = settings.defaultProfile,
            customServerConfigured = settings.customServerConfigured,
            onSelect = {
                viewModel.setDefaultProfile(it)
                showModePicker = false
            },
            onDismiss = { showModePicker = false },
        )
    }
    if (showServer) {
        ServerDialog(
            currentUrl = settings.osrmBaseUrl,
            isCustom = settings.customServerConfigured,
            testState = testState,
            onTest = viewModel::testConnection,
            onSave = {
                viewModel.applyOsrmBaseUrl(it)
                showServer = false
            },
            onUsePublic = {
                viewModel.applyOsrmBaseUrl(MockarrSettings.DEFAULT_OSRM_BASE_URL)
                showServer = false
            },
            onDismiss = { showServer = false },
        )
    }

    // Pinned bar that tints as the list scrolls under it (M3), so rows never just vanish at a hard edge.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.settings_title)) },
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
                .verticalScroll(rememberScrollState()),
        ) {
            TileGrid(
                settings = settings,
                mockReady = setupStatus?.readyToMock,
                onOpenSetup = onOpenSetup,
                onToggleUnits = {
                    val next =
                        if (settings.units == DistanceUnits.MILES) DistanceUnits.KILOMETERS else DistanceUnits.MILES
                    viewModel.setUnits(next)
                },
                onPickMode = { showModePicker = true },
                onServer = { showServer = true },
            )

            SectionHeader(stringResource(R.string.settings_section_playback))
            OptionSwitchRow(
                iconRes = R.drawable.ic_stop,
                title = stringResource(R.string.settings_stay),
                description = stringResource(R.string.settings_stay_desc),
                checked = settings.stayAtDestination,
                onCheckedChange = viewModel::setStayAtDestination,
            )
            OptionSwitchRow(
                iconRes = R.drawable.ic_schedule,
                title = stringResource(R.string.settings_traffic),
                description = stringResource(R.string.settings_traffic_desc),
                checked = settings.trafficSimEnabled,
                onCheckedChange = viewModel::setTrafficSimEnabled,
            )
            OptionSwitchRow(
                iconRes = R.drawable.ic_walk,
                title = stringResource(R.string.settings_offroad_walk),
                description = stringResource(R.string.settings_offroad_walk_desc),
                checked = settings.offRoadWalkEnabled,
                onCheckedChange = viewModel::setOffRoadWalkEnabled,
            )

            SectionHeader(stringResource(R.string.settings_section_gps))
            GpsRows(settings = settings, viewModel = viewModel)

            SectionHeader(stringResource(R.string.settings_section_routing))
            ValueRow(
                iconRes = settings.defaultProfile.iconRes(),
                title = stringResource(R.string.settings_mode),
                description = stringResource(R.string.settings_mode_desc),
                value = stringResource(settings.defaultProfile.shortLabelRes()),
                onClick = { showModePicker = true },
            )
            ValueRow(
                iconRes = R.drawable.ic_server,
                title = stringResource(R.string.settings_server),
                description = stringResource(R.string.settings_server_desc),
                value = stringResource(
                    if (settings.customServerConfigured) {
                        R.string.settings_server_custom
                    } else {
                        R.string.settings_server_public
                    },
                ),
                onClick = { showServer = true },
            )
            OptionSwitchRow(
                iconRes = R.drawable.ic_add_route,
                title = stringResource(R.string.settings_offroad),
                description = stringResource(R.string.settings_offroad_desc),
                checked = settings.offRoadEnabled,
                onCheckedChange = viewModel::setOffRoadEnabled,
            )

            SectionHeader(stringResource(R.string.settings_section_about))
            AboutBlock()
            Spacer(Modifier.height(Tokens.space6))
        }
    }
}

@Composable
private fun TileGrid(
    settings: MockarrSettings,
    mockReady: Boolean?,
    onOpenSetup: () -> Unit,
    onToggleUnits: () -> Unit,
    onPickMode: () -> Unit,
    onServer: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Tokens.space3, vertical = Tokens.space2),
        verticalArrangement = Arrangement.spacedBy(Tokens.space2),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.space2)) {
            SettingTile(
                iconRes = R.drawable.ic_pin_check,
                title = stringResource(R.string.settings_tile_mock),
                value = stringResource(
                    if (mockReady == true) {
                        R.string.settings_tile_mock_ready
                    } else {
                        R.string.settings_tile_mock_not_set_up
                    },
                ),
                valueTone = if (mockReady == true) TileTone.Ready else TileTone.Error,
                onClick = onOpenSetup,
                modifier = Modifier.weight(1f),
            )
            SettingTile(
                iconRes = R.drawable.ic_ruler,
                title = stringResource(R.string.settings_tile_units),
                value = stringResource(
                    if (settings.units == DistanceUnits.MILES) {
                        R.string.settings_units_miles
                    } else {
                        R.string.settings_units_km
                    },
                ),
                onClick = onToggleUnits,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.space2)) {
            SettingTile(
                iconRes = settings.defaultProfile.iconRes(),
                title = stringResource(R.string.settings_tile_mode),
                value = stringResource(settings.defaultProfile.shortLabelRes()),
                onClick = onPickMode,
                modifier = Modifier.weight(1f),
            )
            SettingTile(
                iconRes = R.drawable.ic_server,
                title = stringResource(R.string.settings_tile_server),
                value = stringResource(
                    if (settings.customServerConfigured) {
                        R.string.settings_server_custom
                    } else {
                        R.string.settings_server_public
                    },
                ),
                onClick = onServer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Sliders hold drag state locally and persist once per gesture — a DataStore
 * write per drag event would be a full file rewrite each time. The tick
 * slider is log-scaled so most of its travel covers the low (realistic) end.
 */
@Composable
private fun GpsRows(settings: MockarrSettings, viewModel: SettingsViewModel) {
    var tickHzDrag by remember(settings.tickHz) { mutableStateOf(settings.tickHz) }
    SliderRow(
        iconRes = R.drawable.ic_speed,
        title = stringResource(R.string.settings_tick),
        description = stringResource(R.string.settings_tick_desc),
        valueText = stringResource(R.string.settings_tick_value, tickHzDrag),
        sliderValue = tickHzToSlider(tickHzDrag),
        onSliderChange = { tickHzDrag = sliderToTickHz(it) },
        onSliderFinished = { viewModel.setTickHz(tickHzDrag) },
        contentDescription = stringResource(R.string.settings_tick_cd, tickHzDrag),
    )
    OptionSwitchRow(
        iconRes = R.drawable.ic_route,
        title = stringResource(R.string.settings_wobble),
        description = stringResource(R.string.settings_wobble_desc),
        checked = settings.jitterEnabled,
        onCheckedChange = viewModel::setJitterEnabled,
    )
    if (settings.jitterEnabled) {
        var sigmaDrag by remember(settings.jitterSigmaMeters) { mutableStateOf(settings.jitterSigmaMeters.toFloat()) }
        SliderRow(
            iconRes = R.drawable.ic_target,
            title = stringResource(R.string.settings_wobble_amount),
            description = stringResource(R.string.settings_wobble_amount_desc),
            valueText = stringResource(R.string.settings_wobble_value, sigmaDrag),
            sliderValue = sigmaToSlider(sigmaDrag),
            onSliderChange = { sigmaDrag = sliderToSigma(it) },
            onSliderFinished = { viewModel.setJitterSigmaMeters(sigmaDrag.toDouble()) },
            contentDescription = stringResource(R.string.settings_wobble_cd, sigmaDrag),
        )
    }
}

@Composable
private fun AboutBlock() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.inset, vertical = Tokens.space2),
        verticalArrangement = Arrangement.spacedBy(Tokens.space2),
    ) {
        Text(stringResource(R.string.settings_about_body), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.settings_about_version, version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Text(
            text = stringResource(R.string.settings_about_credits),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val LN_TICK_MIN = ln(MockarrSettings.TICK_HZ_MIN)
private val LN_TICK_MAX = ln(MockarrSettings.TICK_HZ_MAX)
private val SIGMA_MIN = MockarrSettings.JITTER_SIGMA_MIN.toFloat()
private val SIGMA_MAX = MockarrSettings.JITTER_SIGMA_MAX.toFloat()

private fun tickHzToSlider(hz: Double): Float {
    val clamped = hz.coerceIn(MockarrSettings.TICK_HZ_MIN, MockarrSettings.TICK_HZ_MAX)
    return ((ln(clamped) - LN_TICK_MIN) / (LN_TICK_MAX - LN_TICK_MIN)).toFloat()
}

private fun sliderToTickHz(t: Float): Double = exp(LN_TICK_MIN + t * (LN_TICK_MAX - LN_TICK_MIN))

private fun sigmaToSlider(sigma: Float): Float = (sigma - SIGMA_MIN) / (SIGMA_MAX - SIGMA_MIN)

private fun sliderToSigma(t: Float): Float = SIGMA_MIN + t * (SIGMA_MAX - SIGMA_MIN)
