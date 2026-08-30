package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.R
import dev.mockarr.app.playback.HoldSource
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.app.ui.Motion.fadeThrough
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

/**
 * Strava's pause control: one full-width Pause pill that splits into Resume
 * (filled) and Finish (inverse) while paused. Finish ends the drive; the
 * route stays loaded and the Record layout returns.
 */
@Composable
fun PlaybackControls(
    paused: Boolean,
    stopping: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = paused,
        transitionSpec = { fadeThrough() },
        label = "playbackControls",
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) { isPaused ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isPaused) {
                ActionPill(
                    label = stringResource(R.string.sheet_resume),
                    iconRes = R.drawable.ic_play,
                    enabled = !stopping,
                    onClick = onResume,
                )
                ActionPill(
                    label = stringResource(R.string.sheet_finish),
                    iconRes = R.drawable.ic_flag,
                    enabled = !stopping,
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                )
            } else {
                ActionPill(
                    label = stringResource(R.string.sheet_pause),
                    iconRes = R.drawable.ic_pause,
                    enabled = !stopping,
                    onClick = onPause,
                )
            }
        }
    }
}

/**
 * The sheet's grab area: a 48dp tap target around the Material handle. Tapping
 * toggles peek ↔ expanded, and TalkBack gets the same as a custom action —
 * the swipe alone is not an accessible path.
 */
@Composable
fun SheetHandle(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (expanded) R.string.sheet_collapse_cd else R.string.sheet_expand_cd)
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 36dp of layout (the row sits higher); the clickable still gets
            // Compose's 48dp minimum interactive size.
            .height(HANDLE_HEIGHT)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction(label) {
                        onToggle()
                        true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Our own pill: the Material DragHandle carries ~22dp of vertical
        // padding and got clipped inside the 36dp block (session 18).
        Box(
            modifier = Modifier
                .size(HANDLE_PILL_WIDTH, HANDLE_PILL_HEIGHT)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant),
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
private val DISC_SIZE = 28.dp
private val HANDLE_HEIGHT = 36.dp
private val HANDLE_PILL_WIDTH = 32.dp
private val HANDLE_PILL_HEIGHT = 4.dp

internal enum class StripAction { FIX, RELEASE, CANCEL_MOVE }

internal data class StripModel(
    val text: String,
    val tone: StripTone,
    val actionLabel: String? = null,
    val action: StripAction? = null,
    /** Idle prompts ("Plan a drive", "Building a route") show no card at all. */
    val hidden: Boolean = false,
)

/**
 * One line of state, highest-priority state wins. Null means "nothing to say
 * yet" (a hold whose place name is still resolving): the caller keeps showing
 * the previous line rather than raw coordinates.
 */
@Composable
internal fun stripFor(
    state: MapViewModel.UiState,
    playbackState: PlaybackState?,
    holding: MockSessionState.Holding?,
    playing: Boolean,
    builder: Boolean,
    setupReady: Boolean,
    movingStop: String? = null,
): StripModel? = when {
    playing -> playbackStrip(playbackState)
    movingStop != null -> StripModel(
        text = stringResource(R.string.strip_moving_stop, movingStop),
        tone = StripTone.Neutral,
        actionLabel = stringResource(R.string.strip_cancel),
        action = StripAction.CANCEL_MOVE,
    )
    holding != null -> holdingText(holding)?.let { text ->
        StripModel(
            text = text,
            tone = StripTone.Hold,
            actionLabel = stringResource(R.string.strip_stop_hold),
            action = StripAction.RELEASE,
        )
    }
    !setupReady -> StripModel(
        text = stringResource(R.string.strip_not_set_up),
        tone = StripTone.Error,
        actionLabel = stringResource(R.string.strip_fix),
        action = StripAction.FIX,
    )
    state.errorMessage != null -> StripModel(state.errorMessage, StripTone.Error)
    state.isRouting -> StripModel(stringResource(R.string.strip_routing), StripTone.Neutral)
    builder && state.route != null -> StripModel(stringResource(R.string.strip_ready), StripTone.Ready)
    builder -> StripModel(stringResource(R.string.strip_building), StripTone.Neutral, hidden = true)
    state.route != null -> StripModel(stringResource(R.string.strip_route_loaded), StripTone.Ready)
    else -> StripModel(stringResource(R.string.strip_plan), StripTone.Neutral, hidden = true)
}

/**
 * The second line under a hold: the route's own state ("Ready to drive" /
 * "Route ready") stays visible while "Holding at X" takes the top band, so
 * holding never hides the fact that there is a drive to start.
 */
@Composable
internal fun secondaryStripFor(
    state: MapViewModel.UiState,
    holding: MockSessionState.Holding?,
    playing: Boolean,
    builder: Boolean,
    movingStop: String? = null,
): StripModel? = when {
    holding == null || playing || movingStop != null || state.route == null -> null
    builder -> StripModel(stringResource(R.string.strip_ready), StripTone.Ready)
    else -> StripModel(stringResource(R.string.strip_route_loaded), StripTone.Ready)
}

@Composable
private fun playbackStrip(playbackState: PlaybackState?): StripModel = when (playbackState) {
    is PlaybackState.Stopping -> StripModel(stringResource(R.string.strip_stopping), StripTone.Neutral)
    is PlaybackState.Paused -> StripModel(stringResource(R.string.strip_paused), StripTone.Hold)
    is PlaybackState.Dwelling -> {
        val countdown = formatChipCountdown(playbackState.waitSecondsLeft.roundToInt())
        val text = if (playbackState.isDestination) {
            stringResource(R.string.strip_waiting_destination, countdown)
        } else {
            stringResource(R.string.strip_waiting, playbackState.waypointIndex + 1, countdown)
        }
        StripModel(text, StripTone.Hold)
    }
    else -> StripModel(stringResource(R.string.strip_driving), StripTone.Accent)
}

/** Never coordinates: the name, a generic label once the lookup failed, or null while it runs. */
@Composable
private fun holdingText(holding: MockSessionState.Holding): String? {
    val place = holding.placeName
    return when {
        place != null -> stringResource(R.string.strip_holding_at, place)
        holding.source == HoldSource.DESTINATION -> stringResource(R.string.strip_holding_destination)
        holding.source == HoldSource.STOPPED -> stringResource(R.string.strip_holding_stopped)
        holding.nameFailed -> stringResource(R.string.strip_holding_pin)
        else -> null
    }
}

/** The card's trio while driving: time left · distance left · speed, and the progress bar. */
@Composable
internal fun PlaybackStats(
    playbackState: PlaybackState?,
    route: Route?,
    units: DistanceUnits,
    sessionViewModel: MockSessionViewModel,
) {
    val progress = playbackState.progressOrZero
    val total = route?.distanceMeters ?: 0.0
    val remainingMeters = (total * (1 - progress)).coerceAtLeast(0.0)
    val timeLeft = playbackState.remainingSecondsOrNull?.let(::formatDurationShort)
        ?: stringResource(R.string.stat_placeholder)
    // Only this composable follows every fix; the rest of the overlay stays still.
    val fix by sessionViewModel.latestFix.collectAsStateWithLifecycle()
    val speed = fix?.speedMetersPerSecond?.let { formatSpeed(it, units) }
        ?: stringResource(R.string.stat_placeholder)
    StatTrio(
        cells = listOf(
            StatCell(stringResource(R.string.stat_time_left), timeLeft),
            StatCell(stringResource(R.string.stat_distance_left), formatDistance(remainingMeters, units)),
            StatCell(stringResource(R.string.stat_speed), speed),
        ),
    )
    Spacer(Modifier.height(Tokens.space3))
    DriveProgress(progress = progress.toFloat())
}
