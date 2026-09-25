package dev.mockarr.app.ui.screens

import dev.mockarr.core.model.PlaybackState
import kotlin.math.ceil

/**
 * What the band and the drive controls need from the engine's state — and nothing that
 * ticks while the drive simply moves. The screen reads this through `derivedStateOf`:
 * reading [PlaybackState] itself recomposed the whole Map screen on every fix (several
 * times a second), because progress and time left change each time.
 */
internal sealed interface BandPlayback {
    /** Moving along the route (or a state with nothing to say yet). */
    data object Moving : BandPlayback

    data object Paused : BandPlayback

    /** Slowing to a stop after End drive, or ended a beat before the hold takes over. */
    data object WindingDown : BandPlayback

    /**
     * Waiting at a stop, counting down in whole seconds (rounded up, like the marker's chip).
     * [waypointIndex] is -1 for the drive's own off-road pause.
     */
    data class Waiting(
        val secondsLeft: Int,
        val waypointIndex: Int,
        val isDestination: Boolean,
        val skippable: Boolean,
    ) : BandPlayback
}

/** The band's view of [state]; equal across the fixes of an uneventful stretch. */
internal fun bandPlayback(state: PlaybackState?): BandPlayback = when (state) {
    is PlaybackState.Playing -> BandPlayback.Moving
    is PlaybackState.Paused -> BandPlayback.Paused
    is PlaybackState.Dwelling -> BandPlayback.Waiting(
        secondsLeft = ceil(state.waitSecondsLeft).toInt(),
        waypointIndex = state.waypointIndex,
        isDestination = state.isDestination,
        // A real stop's wait can be skipped; the off-road pause is part of the drive itself.
        skippable = state.waypointIndex >= 0 && state.waitSecondsLeft > SKIP_MIN_SECONDS,
    )
    is PlaybackState.Stopping, PlaybackState.Finished, null -> BandPlayback.WindingDown
}

/**
 * How many of [stops] (fractions along the drive, ascending) the drive has reached at
 * [progress]. Changes only as a stop is passed, so it can stand in for progress wherever
 * only "which stops are behind" matters. [tolerance] absorbs the rounding between the
 * bar's fractions and the engine's progress.
 */
internal fun stopsPassed(stops: List<Float>, progress: Double, tolerance: Double = 0.0): Int =
    stops.count { it <= progress + tolerance }

private const val SKIP_MIN_SECONDS = 3.0
