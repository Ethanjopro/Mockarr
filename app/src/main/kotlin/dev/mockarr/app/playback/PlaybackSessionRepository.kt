package dev.mockarr.app.playback

import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.SimulatedFix
import dev.mockarr.core.simulation.SimulationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the active playback session. The service hosts the
 * engine and pushes state here; ViewModels observe and send control calls back.
 * No Binder plumbing needed.
 */
@Singleton
class PlaybackSessionRepository @Inject constructor() {

    private val _state = MutableStateFlow<PlaybackState?>(null)
    val state: StateFlow<PlaybackState?> = _state.asStateFlow()

    private val _latestFix = MutableStateFlow<SimulatedFix?>(null)
    val latestFix: StateFlow<SimulatedFix?> = _latestFix.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var engine: SimulationEngine? = null

    /** Staged for the service, which consumes it (routes exceed intent-extra limits). */
    private var pendingRoute: Route? = null

    /** Stage a route for the next session; the caller then starts the service. */
    fun requestStart(route: Route) {
        pendingRoute = route
    }

    fun pause() {
        engine?.pause()
    }

    fun resume() {
        engine?.resume()
    }

    fun stop() {
        engine?.stop()
    }

    fun setSpeedMultiplier(multiplier: Double) {
        engine?.setSpeedMultiplier(multiplier)
    }

    fun consumeError() {
        _error.value = null
    }

    internal fun consumePendingRoute(): Route? = pendingRoute.also { pendingRoute = null }

    internal fun sessionStarted(engine: SimulationEngine) {
        this.engine = engine
        _error.value = null
    }

    internal fun updateState(state: PlaybackState) {
        _state.value = state
    }

    internal fun updateFix(fix: SimulatedFix) {
        _latestFix.value = fix
    }

    internal fun sessionEnded() {
        engine = null
        _state.value = null
        _latestFix.value = null
    }

    internal fun reportError(message: String) {
        _error.value = message
        sessionEnded()
    }
}
