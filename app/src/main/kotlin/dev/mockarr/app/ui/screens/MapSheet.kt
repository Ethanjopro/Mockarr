package dev.mockarr.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.R
import dev.mockarr.app.playback.HoldSource
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.app.ui.formatDistance
import dev.mockarr.app.ui.formatDurationShort
import dev.mockarr.app.ui.map.formatChipCountdown
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.progressOrZero
import dev.mockarr.core.model.remainingSecondsOrNull
import dev.mockarr.core.simulation.SimulationEngine
import kotlin.math.roundToInt

/** What the strip says about the session, in colour. */
enum class StripTone { Neutral, Ready, Accent, Hold, Error }

/**
 * The sheet's top edge: one line of state copy on a tinted band, with the
 * drag handle riding inside it. Colour crossfades between states so a
 * transition reads as one surface changing, not a card swap.
 */
@Composable
fun StatusStrip(
    text: String,
    tone: StripTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MockarrTheme.colors
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        StripTone.Neutral -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        StripTone.Ready -> colors.readyContainer to colors.onReadyContainer
        StripTone.Accent -> scheme.primaryContainer to scheme.onPrimaryContainer
        StripTone.Hold -> colors.holdContainer to colors.onHoldContainer
        StripTone.Error -> scheme.errorContainer to scheme.onErrorContainer
    }
    val background by animateColorAsState(container, label = "stripBackground")
    val foreground by animateColorAsState(content, label = "stripForeground")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = Tokens.inset),
    ) {
        Box(
            modifier = Modifier
                .padding(top = Tokens.space2)
                .size(width = HANDLE_WIDTH, height = HANDLE_HEIGHT)
                .background(foreground.copy(alpha = HANDLE_ALPHA), CircleShape)
                .align(Alignment.CenterHorizontally),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = STRIP_MIN_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(Tokens.space2))
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = foreground),
                ) { Text(actionLabel) }
            }
        }
    }
}

/** One label-over-value cell of the stat trio. */
data class StatCell(val label: String, val value: String)

/** Three equal cells — no hero numeral (design brief). Tabular figures keep the columns still. */
@Composable
fun StatTrio(cells: List<StatCell>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cell.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = cell.value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Progress under the trio: thin, accent, no label — the trio already says the numbers. */
@Composable
fun DriveProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(PROGRESS_HEIGHT),
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {},
    )
}

/** Pause/Resume + Stop + the speed pill, one row. */
@Composable
fun PlaybackControls(
    paused: Boolean,
    stopping: Boolean,
    speedMultiplier: Double,
    speedExpanded: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onToggleSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (paused) {
            Button(onClick = onResume, enabled = !stopping, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                Spacer(Modifier.width(Tokens.space2))
                Text(stringResource(R.string.sheet_resume))
            }
        } else {
            Button(onClick = onPause, enabled = !stopping, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_pause), contentDescription = null)
                Spacer(Modifier.width(Tokens.space2))
                Text(stringResource(R.string.sheet_pause))
            }
        }
        OutlinedButton(onClick = onStop, enabled = !stopping) {
            Icon(painterResource(R.drawable.ic_stop), contentDescription = null)
            Spacer(Modifier.width(Tokens.space2))
            Text(stringResource(R.string.sheet_stop))
        }
        val speedText = formatMultiplier(speedMultiplier)
        val speedDescription = stringResource(R.string.sheet_speed_cd, speedText)
        FilterChip(
            selected = speedExpanded,
            onClick = onToggleSpeed,
            label = { Text(stringResource(R.string.sheet_speed_value, speedText)) },
            modifier = Modifier.semantics { contentDescription = speedDescription },
        )
    }
}

/** The chip row the speed pill opens. */
@Composable
fun SpeedChips(
    speedMultiplier: Double,
    onSpeedChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.sheet_speed).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SPEED_PRESETS.forEach { preset ->
            FilterChip(
                selected = preset == speedMultiplier,
                onClick = { onSpeedChange(preset) },
                label = { Text(stringResource(R.string.sheet_speed_value, formatMultiplier(preset))) },
            )
        }
    }
}

/** A numbered disc matching the map markers, for the stop list. */
@Composable
fun StopDisc(number: Int, isStart: Boolean, isEnd: Boolean, modifier: Modifier = Modifier) {
    val palette = MockarrTheme.colors.map
    val fill = Color(
        when {
            isStart -> palette.stopStart
            isEnd -> palette.stopEnd
            else -> palette.stopVia
        },
    )
    val ink = Color(if (isEnd) palette.stopRing else palette.stopText)
    Box(
        modifier = modifier.size(DISC_SIZE).background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = ink,
        )
    }
}

val SPEED_PRESETS: List<Double> = listOf(
    SimulationEngine.MIN_MULTIPLIER,
    HALF_SPEED,
    1.0,
    DOUBLE_SPEED,
    SimulationEngine.MAX_MULTIPLIER,
)

private const val HALF_SPEED = 0.5
private const val DOUBLE_SPEED = 2.0
private const val HANDLE_ALPHA = 0.4f
private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp
private val STRIP_MIN_HEIGHT = 40.dp
private val PROGRESS_HEIGHT = 4.dp
private val DISC_SIZE = 28.dp

internal enum class StripAction { FIX, RELEASE }

internal data class StripModel(
    val text: String,
    val tone: StripTone,
    val actionLabel: String? = null,
    val action: StripAction? = null,
)

/** One line of state, highest-priority state wins. */
@Composable
internal fun stripFor(
    state: MapViewModel.UiState,
    playbackState: PlaybackState?,
    holding: MockSessionState.Holding?,
    playing: Boolean,
    setupReady: Boolean,
): StripModel = when {
    playing -> playbackStrip(playbackState)
    holding != null -> StripModel(
        text = holdingText(holding),
        tone = StripTone.Hold,
        actionLabel = stringResource(R.string.strip_stop_hold),
        action = StripAction.RELEASE,
    )
    !setupReady -> StripModel(
        text = stringResource(R.string.strip_not_set_up),
        tone = StripTone.Error,
        actionLabel = stringResource(R.string.strip_fix),
        action = StripAction.FIX,
    )
    state.errorMessage != null -> StripModel(state.errorMessage, StripTone.Error)
    state.isRouting -> StripModel(stringResource(R.string.strip_routing), StripTone.Neutral)
    state.route != null -> StripModel(stringResource(R.string.strip_ready), StripTone.Ready)
    else -> StripModel(stringResource(R.string.strip_plan), StripTone.Neutral)
}

@Composable
private fun playbackStrip(playbackState: PlaybackState?): StripModel = when (playbackState) {
    is PlaybackState.Stopping -> StripModel(stringResource(R.string.strip_stopping), StripTone.Neutral)
    is PlaybackState.Paused -> StripModel(stringResource(R.string.strip_paused), StripTone.Neutral)
    is PlaybackState.Dwelling -> StripModel(
        text = stringResource(
            R.string.strip_waiting,
            playbackState.waypointIndex + 1,
            formatChipCountdown(playbackState.waitSecondsLeft.roundToInt()),
        ),
        tone = StripTone.Hold,
    )
    else -> StripModel(stringResource(R.string.strip_driving), StripTone.Accent)
}

@Composable
private fun holdingText(holding: MockSessionState.Holding): String {
    val place = holding.placeName
    return when {
        place != null -> stringResource(R.string.strip_holding_at, place)
        holding.source == HoldSource.DESTINATION -> stringResource(R.string.strip_holding_destination)
        else -> stringResource(
            R.string.strip_holding_coords,
            "%.4f, %.4f".format(holding.position.latitude, holding.position.longitude),
        )
    }
}

/** Peek while driving: the stat trio, progress, and the control row. */
@Composable
internal fun PlaybackPeek(
    playbackState: PlaybackState?,
    route: Route?,
    units: DistanceUnits,
    speedMultiplier: Double,
    speedExpanded: Boolean,
    sessionViewModel: MockSessionViewModel,
    onToggleSpeed: () -> Unit,
) {
    val progress = playbackState.progressOrZero
    val total = route?.distanceMeters ?: 0.0
    val remainingMeters = (total * (1 - progress)).coerceAtLeast(0.0)
    val timeLeft = playbackState.remainingSecondsOrNull?.let(::formatDurationShort) ?: "—"
    // Only this cell follows every fix; the rest of the sheet stays still.
    val fix by sessionViewModel.latestFix.collectAsStateWithLifecycle()
    val speed = fix?.speedMetersPerSecond?.let { formatSpeed(it, units) } ?: "—"
    Column(modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space3)) {
        StatTrio(
            cells = listOf(
                StatCell(stringResource(R.string.stat_time_left), timeLeft),
                StatCell(stringResource(R.string.stat_distance_left), formatDistance(remainingMeters, units)),
                StatCell(stringResource(R.string.stat_speed), speed),
            ),
        )
        Spacer(Modifier.height(Tokens.space3))
        DriveProgress(progress = progress.toFloat())
        Spacer(Modifier.height(Tokens.space3))
        PlaybackControls(
            paused = playbackState is PlaybackState.Paused,
            stopping = playbackState is PlaybackState.Stopping,
            speedMultiplier = speedMultiplier,
            speedExpanded = speedExpanded,
            onPause = sessionViewModel::pause,
            onResume = sessionViewModel::resume,
            onStop = sessionViewModel::stopPlayback,
            onToggleSpeed = onToggleSpeed,
        )
    }
}
