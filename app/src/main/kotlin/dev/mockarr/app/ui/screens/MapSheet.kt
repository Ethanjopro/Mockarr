package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
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
import dev.mockarr.app.ui.map.formatChipCountdown
import dev.mockarr.app.ui.rememberFormatter
import dev.mockarr.app.ui.theme.ActionPill
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.progressOrZero
import dev.mockarr.core.model.remainingSecondsOrNull
import dev.mockarr.core.simulation.SimulationEngine
import kotlin.math.ceil

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
    // 36dp of layout (the row sits higher) around a full 48dp touch target:
    // requiredHeight lets the clickable overflow the block by 6dp each side.
    Box(modifier = modifier.fillMaxWidth().height(HANDLE_HEIGHT), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(Tokens.touchTarget)
                .clickable(onClick = onToggle, role = Role.Button)
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
                    .size(Tokens.space8, HANDLE_PILL_HEIGHT)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
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
        // Scrolls: 4x must stay reachable at large font scales and 720px-wide displays.
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
        modifier = modifier.size(Tokens.discSize).background(fill, CircleShape),
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
private val HANDLE_HEIGHT = 36.dp
private val HANDLE_PILL_HEIGHT = 4.dp
private const val SKIP_MIN_SECONDS = 3.0

internal enum class StripAction { FIX, RELEASE, CANCEL_MOVE, ADD_STOP, SKIP_WAIT }

internal data class StripModel(
    val text: String,
    val tone: StripTone,
    val actionLabel: String? = null,
    val action: StripAction? = null,
    /** Idle prompts ("Plan a drive", "Building a route") show no card at all. */
    val hidden: Boolean = false,
)

/**
 * One line of state, highest-priority state wins — the card's single band,
 * from the idle prompt through Ready, Driving, Arrived and Holding. Null means
 * "nothing to say yet" (a hold whose place name is still resolving): the
 * caller keeps showing the previous line rather than raw coordinates. The
 * trio under the band says whether a route is loaded; the band never repeats it.
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
    arrived: Boolean = false,
    searchedPlace: String? = null,
): StripModel? = when {
    playing -> playbackStrip(playbackState, state.profile)
    movingStop != null -> StripModel(
        text = stringResource(R.string.strip_moving_stop, movingStop),
        tone = StripTone.Neutral,
        actionLabel = stringResource(R.string.strip_cancel),
        action = StripAction.CANCEL_MOVE,
    )
    // The end of a drive gets its own moment before the band settles into Holding.
    arrived -> StripModel(
        text = holding?.placeName?.let { stringResource(R.string.strip_arrived_at, it) }
            ?: stringResource(R.string.strip_arrived),
        tone = StripTone.Ready,
        actionLabel = if (holding != null) stringResource(R.string.strip_stop_hold) else null,
        action = if (holding != null) StripAction.RELEASE else null,
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
    state.routingError != null -> StripModel(stringResource(state.routingError.stripRes()), StripTone.Error)
    state.isRouting -> StripModel(stringResource(R.string.strip_routing), StripTone.Neutral)
    // The searched place, until it is a stop: its name and the verb that drops it there. Sits above the
    // ready strip on purpose — otherwise a third stop could never be added from search.
    searchedPlace != null -> StripModel(
        text = searchedPlace,
        tone = StripTone.Accent,
        actionLabel = stringResource(R.string.strip_add_stop),
        action = StripAction.ADD_STOP,
    )
    builder && state.route != null -> StripModel(stringResource(state.profile.readyLabelRes()), StripTone.Ready)
    builder -> StripModel(stringResource(R.string.strip_building), StripTone.Neutral, hidden = true)
    state.route != null -> StripModel(stringResource(R.string.strip_route_loaded), StripTone.Ready)
    // The empty card: the one instruction a cold start needs.
    else -> StripModel(stringResource(R.string.strip_idle), StripTone.Neutral)
}

private fun RoutingError.stripRes(): Int = when (this) {
    RoutingError.NO_ROUTE -> R.string.strip_routing_failed
    RoutingError.BUSY -> R.string.strip_routing_busy
    RoutingError.OFFLINE -> R.string.strip_routing_offline
    RoutingError.OTHER -> R.string.strip_routing_failed
}

@Composable
private fun playbackStrip(playbackState: PlaybackState?, profile: RoutingProfile): StripModel = when (playbackState) {
    is PlaybackState.Stopping -> StripModel(stringResource(R.string.strip_stopping), StripTone.Neutral)
    is PlaybackState.Paused -> StripModel(stringResource(R.string.strip_paused), StripTone.Hold)
    is PlaybackState.Dwelling -> {
        // Whole seconds rounded up, like the chip over the marker — the two never disagree.
        val countdown = formatChipCountdown(ceil(playbackState.waitSecondsLeft).toInt())
        val text = when {
            playbackState.isDestination -> stringResource(R.string.strip_waiting_destination, countdown)
            // A synthesized off-road pause carries no waypoint (index -1).
            playbackState.waypointIndex < 0 -> stringResource(R.string.strip_offroad_pause, countdown)
            else -> stringResource(R.string.strip_waiting, playbackState.waypointIndex + 1, countdown)
        }
        // A real stop's wait can be skipped; the off-road pause is part of the drive itself.
        val skippable = playbackState.waypointIndex >= 0 && playbackState.waitSecondsLeft > SKIP_MIN_SECONDS
        StripModel(
            text = text,
            tone = StripTone.Hold,
            actionLabel = if (skippable) stringResource(R.string.strip_skip_wait) else null,
            action = if (skippable) StripAction.SKIP_WAIT else null,
        )
    }
    else -> StripModel(stringResource(profile.movingLabelRes()), StripTone.Accent)
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
    val formatter = rememberFormatter()
    val progress = playbackState.progressOrZero
    val total = route?.distanceMeters ?: 0.0
    val remainingMeters = (total * (1 - progress)).coerceAtLeast(0.0)
    val timeLeft = playbackState.remainingSecondsOrNull?.let(formatter::duration)
        ?: stringResource(R.string.stat_placeholder)
    // Only this composable follows every fix; the rest of the overlay stays still.
    val fix by sessionViewModel.latestFix.collectAsStateWithLifecycle()
    val speed = fix?.speedMetersPerSecond?.let { formatSpeed(it, units) }
        ?: stringResource(R.string.stat_placeholder)
    StatTrio(
        cells = listOf(
            StatCell(stringResource(R.string.stat_time_left), timeLeft),
            StatCell(stringResource(R.string.stat_distance_left), formatter.distance(remainingMeters, units)),
            StatCell(stringResource(R.string.stat_speed), speed),
        ),
    )
    Spacer(Modifier.height(Tokens.space3))
    DriveProgress(progress = progress.toFloat())
}
