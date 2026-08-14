package dev.mockarr.core.model

/** Lifecycle of a route playback session. */
sealed interface PlaybackState {
    /** @property progress fraction of the route distance covered, in `0.0..1.0`. */
    data class Playing(val progress: Double) : PlaybackState

    data class Paused(val progress: Double) : PlaybackState

    /** Decelerating to a stop before finishing, so playback never teleports. */
    data object Stopping : PlaybackState

    data object Finished : PlaybackState
}

/** Route progress of the session, or 0.0 when there is none / it is winding down. */
val PlaybackState?.progressOrZero: Double
    get() = when (this) {
        is PlaybackState.Playing -> progress
        is PlaybackState.Paused -> progress
        else -> 0.0
    }
