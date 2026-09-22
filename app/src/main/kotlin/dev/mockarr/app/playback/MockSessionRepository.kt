package dev.mockarr.app.playback

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.SessionSnapshot
import dev.mockarr.core.model.SimulatedFix
import dev.mockarr.core.simulation.SimulationEngine
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        /** The lookup failed or timed out: the banner falls back to a generic label. */
        val nameFailed: Boolean = false,
    ) : MockSessionState
}

enum class HoldSource { PIN, DESTINATION, STOPPED }

/** How the last drive ended: on its own at the destination, or cut short (Stop, Finish, a release). */
enum class DriveOutcome { ARRIVED, STOPPED }

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

    /**
     * The drive being played, so a map that opens mid-drive (a new Activity,
     * a recreation) can draw it; null outside a drive.
     */
    data class LiveDrive(val route: Route, val profile: RoutingProfile, val saved: Boolean)

    private val _liveDrive = MutableStateFlow<LiveDrive?>(null)
    val liveDrive: StateFlow<LiveDrive?> = _liveDrive.asStateFlow()

    /**
     * Set before the session leaves Playing, so whoever sees the drive end can
     * tell an arrival from a Stop — even when it was not watching the deceleration.
     */
    private val _driveOutcome = MutableStateFlow<DriveOutcome?>(null)
    val driveOutcome: StateFlow<DriveOutcome?> = _driveOutcome.asStateFlow()
    private var stopRequested = false

    // Latest-wins thumbstick nudge targets. Only the service's hold job
    // collects, so nudges are structurally dead outside a hold.
    private val _holdMoves = MutableSharedFlow<LatLng>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    internal val holdMoves: SharedFlow<LatLng> = _holdMoves.asSharedFlow()

    /** What the service should start next (routes exceed intent-extra limits). */
    data class PendingSession(
        val route: Route,
        val profile: RoutingProfile,
        /** Set when picking an interrupted drive back up; null starts from the route's beginning. */
        val resumeFrom: SimulationEngine.ResumePoint? = null,
        /** The route is in Saved routes as-is (so a map adopting the drive won't offer Save). */
        val saved: Boolean = false,
    )

    private var pendingSession: PendingSession? = null

    /** A drive or hold cut short by process death, waiting for the user's Resume / Discard. */
    private val _interrupted = MutableStateFlow<SessionSnapshot?>(null)
    val interrupted: StateFlow<SessionSnapshot?> = _interrupted.asStateFlow()

    internal fun setInterrupted(snapshot: SessionSnapshot?) {
        _interrupted.value = snapshot
    }

    fun clearInterrupted() {
        _interrupted.value = null
    }

    /** Thumbstick: move the held location to [position]; ignored unless holding. */
    fun requestHoldMove(position: LatLng) {
        if (_session.value is MockSessionState.Holding) _holdMoves.tryEmit(position)
    }

    /** Stage the next session; the caller then starts the service. */
    fun requestStart(
        route: Route,
        profile: RoutingProfile,
        resumeFrom: SimulationEngine.ResumePoint? = null,
        saved: Boolean = false,
    ) {
        pendingSession = PendingSession(route, profile, resumeFrom, saved)
    }

    fun pause() {
        engine?.pause()
    }

    fun resume() {
        engine?.resume()
    }

    fun stop() {
        val running = engine ?: return
        stopRequested = true
        running.stop()
    }

    /** Playback pace, owned here so the chips, the running engine and the next drive agree. */
    private val _speedMultiplier = MutableStateFlow(1.0)
    val speedMultiplier: StateFlow<Double> = _speedMultiplier.asStateFlow()

    fun setSpeedMultiplier(multiplier: Double) {
        val clamped = multiplier.coerceIn(SimulationEngine.MIN_MULTIPLIER, SimulationEngine.MAX_MULTIPLIER)
        _speedMultiplier.value = clamped
        engine?.setSpeedMultiplier(clamped)
    }

    /** Live-updates one stop's wait on the running engine; a no-op between drives. */
    fun setWaypointWait(waypointIndex: Int, waitSeconds: Int) {
        engine?.setWaypointWait(waypointIndex, waitSeconds)
    }

    fun consumeError() {
        _error.value = null
    }

    internal fun consumePendingSession(): PendingSession? = pendingSession.also { pendingSession = null }

    internal fun playingStarted(engine: SimulationEngine, drive: LiveDrive) {
        this.engine = engine
        stopRequested = false
        _driveOutcome.value = null
        _liveDrive.value = drive
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

    /** A nudge landed: keep the source, drop the stale name until re-resolved. */
    internal fun holdMoved(position: LatLng) {
        val current = _session.value
        if (current is MockSessionState.Holding) {
            _session.value = current.copy(position = position, placeName = null, nameFailed = false)
        }
    }

    /** Attach a resolved place name (null = lookup failed) — only if we're still holding that same spot. */
    internal fun holdNameResolved(position: LatLng, name: String?) {
        val current = _session.value
        if (current is MockSessionState.Holding && current.position == position) {
            _session.value = current.copy(placeName = name, nameFailed = name == null)
        }
    }

    /** The playback engine is done; the session may continue as a hold. */
    internal fun engineEnded() {
        if (engine != null) _driveOutcome.value = if (stopRequested) DriveOutcome.STOPPED else DriveOutcome.ARRIVED
        engine = null
        _liveDrive.value = null
        _state.value = null
        _latestFix.value = null
    }

    /** Test providers were removed — the device is back on its real location. */
    internal fun sessionReleased() {
        // A release mid-drive (an error, a system kill) is never an arrival.
        if (engine != null) stopRequested = true
        engineEnded()
        _session.value = MockSessionState.Idle
    }

    internal fun reportError(message: String) {
        _error.value = message
    }
}
