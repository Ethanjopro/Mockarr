package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.R
import dev.mockarr.app.playback.HoldSource
import dev.mockarr.app.playback.MockSessionRepository
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.app.playback.StopRef
import dev.mockarr.app.playback.stopRef
import dev.mockarr.app.ui.Motion.fadeThrough
import dev.mockarr.app.ui.map.formatChipCountdown
import dev.mockarr.app.ui.rememberFormatter
import dev.mockarr.app.ui.theme.ActionPill
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.OutlinedActionPill
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.SessionSnapshot
import dev.mockarr.core.model.progressOrZero
import dev.mockarr.core.model.remainingSecondsOrNull
import dev.mockarr.core.simulation.SimulationEngine
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Strava's pause control: one full-width Pause pill that splits into End
 * drive (outlined, left) and Resume (filled, right) while paused. End drive
 * finishes the drive and clears its route from the map.
 */
@Composable
fun PlaybackControls(
    profile: RoutingProfile,
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
                // The way out on the left, outlined; the way on, filled — like every two-up row.
                OutlinedActionPill(
                    label = stringResource(profile.endLabelRes()),
                    iconRes = R.drawable.ic_flag,
                    enabled = !stopping,
                    onClick = onFinish,
                )
                ActionPill(
                    label = stringResource(R.string.sheet_resume),
                    iconRes = R.drawable.ic_play,
                    enabled = !stopping,
                    onClick = onResume,
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedChips(
    speedMultiplier: Double,
    onSpeedChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Wraps: the popover is narrower than five chips, and a scroll row hid 2×/4×
    // past its edge with nothing to say so (sessions 35/37).
    FlowRow(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
        verticalArrangement = Arrangement.spacedBy(Tokens.space1),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.sheet_speed).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
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
private const val PERCENT = 100

internal enum class StripAction { FIX, RELEASE, CANCEL_MOVE, ADD_STOP, SKIP_WAIT, RESUME_SESSION }

internal data class StripModel(
    val text: String,
    val tone: StripTone,
    val actionLabel: String? = null,
    val action: StripAction? = null,
    /**
     * What TalkBack's live region says, when [text] carries a ticking value: the
     * wait's countdown changes every second, and a live region must only speak
     * on real state changes (audit, session 41). Null speaks [text].
     */
    val spoken: String? = null,
    /** Idle prompts ("Plan a drive", "Building a route") show no card at all. */
    val hidden: Boolean = false,
)

/**
 * One line of state, highest-priority state wins — the card's single band,
 * from the idle prompt through Ready, Driving, Arrived and Holding. Null means
 * "nothing new to say" (a drive slowing to its end): the caller keeps showing
 * the previous line. The trio under the band says whether a route is loaded;
 * the band never repeats it.
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
    interrupted: SessionSnapshot? = null,
    /** The drive in flight, for naming the stop it waits at. */
    drive: MockSessionRepository.LiveDrive? = null,
): StripModel? = when {
    playing -> playbackStrip(playbackState, state.profile, drive)
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
    // A session the process death cut short, offered back only while nothing else is going on.
    interrupted != null && state.route == null && !builder -> StripModel(
        text = if (interrupted.kind == SessionSnapshot.Kind.HOLDING) {
            stringResource(R.string.strip_interrupted_hold)
        } else {
            stringResource(R.string.strip_interrupted_drive, (interrupted.progress * PERCENT).roundToInt())
        },
        tone = StripTone.Hold,
        actionLabel = if (interrupted.kind == SessionSnapshot.Kind.HOLDING) {
            stringResource(R.string.strip_resume_hold)
        } else {
            stringResource(R.string.strip_resume_drive)
        },
        action = StripAction.RESUME_SESSION,
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
    state.route != null -> StripModel(stringResource(state.profile.readyLabelRes()), StripTone.Ready)
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
private fun playbackStrip(
    playbackState: PlaybackState?,
    profile: RoutingProfile,
    drive: MockSessionRepository.LiveDrive?,
): StripModel? = when (playbackState) {
    // The slow-down after End drive keeps the band as it was (its controls go inert): a
    // "Stopping…" band flashed for half a second between Paused and Holding.
    is PlaybackState.Stopping -> null
    is PlaybackState.Paused -> StripModel(stringResource(R.string.strip_paused), StripTone.Hold)
    is PlaybackState.Dwelling -> {
        // Whole seconds rounded up, like the chip over the marker — the two never disagree.
        val countdown = formatChipCountdown(ceil(playbackState.waitSecondsLeft).toInt())
        // A synthesized off-road pause carries no waypoint (index -1); a stop is named as the user
        // knows it — "Waiting at Reunion Tower", or its number in their route, never the drive's.
        val stop = playbackState.waypointIndex
            .takeIf { it >= 0 }
            ?.let { drive.stopRef(it, playbackState.isDestination) }
        val text = when (stop) {
            null -> stringResource(R.string.strip_offroad_pause, countdown)
            is StopRef.Named -> stringResource(R.string.strip_waiting_at, stop.name, countdown)
            StopRef.Destination -> stringResource(R.string.strip_waiting_destination, countdown)
            is StopRef.Numbered -> stringResource(R.string.strip_waiting, stop.number, countdown)
        }
        val spoken = when (stop) {
            null -> stringResource(R.string.strip_offroad_spoken)
            is StopRef.Named -> stringResource(R.string.strip_waiting_at_spoken, stop.name)
            StopRef.Destination -> stringResource(R.string.strip_waiting_destination_spoken)
            is StopRef.Numbered -> stringResource(R.string.strip_waiting_spoken, stop.number)
        }
        // A real stop's wait can be skipped; the off-road pause is part of the drive itself.
        val skippable = playbackState.waypointIndex >= 0 && playbackState.waitSecondsLeft > SKIP_MIN_SECONDS
        StripModel(
            text = text,
            tone = StripTone.Hold,
            actionLabel = if (skippable) stringResource(R.string.strip_skip_wait) else null,
            action = if (skippable) StripAction.SKIP_WAIT else null,
            spoken = spoken,
        )
    }
    else -> StripModel(stringResource(profile.movingLabelRes()), StripTone.Accent)
}

/**
 * Never coordinates: the place's name, or a generic label until (or unless) the lookup
 * finds one. A new hold used to wait for its name and keep the previous state on the
 * band meanwhile — "Ready to drive" or the idle prompt beside the new pin for seconds.
 */
@Composable
private fun holdingText(holding: MockSessionState.Holding): String {
    val place = holding.placeName
    return when {
        place != null -> stringResource(R.string.strip_holding_at, place)
        holding.source == HoldSource.DESTINATION -> stringResource(R.string.strip_holding_destination)
        holding.source == HoldSource.STOPPED -> stringResource(R.string.strip_holding_stopped)
        holding.nameFailed -> stringResource(R.string.strip_holding_pin)
        else -> stringResource(R.string.strip_holding_pending)
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
    val secondsLeft = playbackState.remainingSecondsOrNull
    val timeLeft = secondsLeft?.let(formatter::duration) ?: stringResource(R.string.stat_placeholder)
    // Only this composable follows every fix; the rest of the overlay stays still.
    val fix by sessionViewModel.latestFix.collectAsStateWithLifecycle()
    val speedMps = fix?.speedMetersPerSecond
    val speed = speedMps?.let { formatSpeed(it, units) } ?: stringResource(R.string.stat_placeholder)
    StatTrio(
        cells = listOf(
            StatCell(stringResource(R.string.stat_time_left), timeLeft),
            StatCell(stringResource(R.string.stat_distance_left), formatter.distance(remainingMeters, units)),
            // Only speed rolls (Ethan): the one number that should feel like an instrument.
            StatCell(stringResource(R.string.stat_speed), speed, magnitude = speedMps, rolls = true),
        ),
    )
    Spacer(Modifier.height(Tokens.space3))
    val stops = remember(route) { stopFractions(route) }
    DriveProgress(progress = progress.toFloat(), stops = stops)
}
