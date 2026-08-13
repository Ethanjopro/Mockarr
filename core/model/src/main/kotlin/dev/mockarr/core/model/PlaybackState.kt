package dev.mockarr.core.model

/** Lifecycle of a route playback session. */
sealed interface PlaybackState {
    data object Idle : PlaybackState

    /** @property progress fraction of the route distance covered, in `0.0..1.0`. */
    data class Playing(val progress: Double) : PlaybackState

    data class Paused(val progress: Double) : PlaybackState

    /** Decelerating to a stop before finishing, so playback never teleports. */
    data object Stopping : PlaybackState

    data object Finished : PlaybackState
}
