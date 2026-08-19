package dev.mockarr.core.model

/** Lifecycle of a route playback session. */
sealed interface PlaybackState {
    /**
     * @property progress fraction of the route distance covered, in `0.0..1.0`
     * @property remainingSeconds estimated wall-clock time to the destination
     *   (route timing scaled by the speed multiplier)
     */
    data class Playing(val progress: Double, val remainingSeconds: Double = 0.0) : PlaybackState

    data class Paused(val progress: Double, val remainingSeconds: Double = 0.0) : PlaybackState

    /** Waiting at a user-set stop; playback resumes when the wait elapses. */
    data class Dwelling(
        val progress: Double,
        val remainingSeconds: Double = 0.0,
        val waitSecondsLeft: Double = 0.0,
    ) : PlaybackState

    /** Decelerating to a stop before finishing, so playback never teleports. */
    data object Stopping : PlaybackState

    data object Finished : PlaybackState
}

/** Route progress of the session, or 0.0 when there is none / it is winding down. */
val PlaybackState?.progressOrZero: Double
    get() = when (this) {
        is PlaybackState.Playing -> progress
        is PlaybackState.Paused -> progress
        is PlaybackState.Dwelling -> progress
        else -> 0.0
    }

/** Estimated time left, or null when the session isn't moving along a route. */
val PlaybackState?.remainingSecondsOrNull: Double?
    get() = when (this) {
        is PlaybackState.Playing -> remainingSeconds
        is PlaybackState.Paused -> remainingSeconds
        is PlaybackState.Dwelling -> remainingSeconds
        else -> null
    }
