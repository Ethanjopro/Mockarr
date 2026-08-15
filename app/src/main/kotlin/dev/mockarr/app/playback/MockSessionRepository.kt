package dev.mockarr.app.playback

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.SimulatedFix
import dev.mockarr.core.simulation.SimulationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the mocked location is doing right now, app-wide. */
sealed interface MockSessionState {
    /** No test providers registered — the device sees its real location. */
    data object Idle : MockSessionState

    /** A route playback session is feeding fixes. */
    data object Playing : MockSessionState

    /** The location is pinned to one spot until released or replaced. */
    data class Holding(
        val position: LatLng,
        val source: HoldSource,
        /** Reverse-geocoded short name of the spot; null until (or unless) resolved. */
        val placeName: String? = null,
    ) : MockSessionState
}

enum class HoldSource { PIN, DESTINATION }

/**
 * Single source of truth for the active mock session. The service hosts the
 * engine/hold ticker and pushes state here; ViewModels observe and send
 * control calls back. No Binder plumbing needed.
 */
@Singleton
class MockSessionRepository @Inject constructor() {

    private val _session = MutableStateFlow<MockSessionState>(MockSessionState.Idle)
    val session: StateFlow<MockSessionState> = _session.asStateFlow()

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

    internal fun playingStarted(engine: SimulationEngine) {
        this.engine = engine
        _error.value = null
        _session.value = MockSessionState.Playing
    }

    internal fun updateState(state: PlaybackState) {
        _state.value = state
    }

    internal fun updateFix(fix: SimulatedFix) {
        _latestFix.value = fix
    }

    internal fun holdStarted(position: LatLng, source: HoldSource) {
        _session.value = MockSessionState.Holding(position, source)
    }

    /** Attach a resolved place name — only if we're still holding that same spot. */
    internal fun holdNameResolved(position: LatLng, name: String) {
        val current = _session.value
        if (current is MockSessionState.Holding && current.position == position) {
            _session.value = current.copy(placeName = name)
        }
    }

    /** The playback engine is done; the session may continue as a hold. */
    internal fun engineEnded() {
        engine = null
        _state.value = null
        _latestFix.value = null
    }

    /** Test providers were removed — the device is back on its real location. */
    internal fun sessionReleased() {
        engineEnded()
        _session.value = MockSessionState.Idle
    }

    internal fun reportError(message: String) {
        _error.value = message
    }
}
