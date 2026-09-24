package dev.mockarr.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mockarr.app.R
import dev.mockarr.app.playback.DriveInPlanner
import dev.mockarr.app.playback.DriveOutcome
import dev.mockarr.app.playback.MockSessionRepository
import dev.mockarr.app.playback.MockSessionService
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.app.ui.RouteHandoff
import dev.mockarr.core.data.SessionSnapshotStore
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.SessionSnapshot
import dev.mockarr.core.simulation.SimulationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

@HiltViewModel
class MockSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MockSessionRepository,
    private val snapshotStore: SessionSnapshotStore,
    private val routeHandoff: RouteHandoff,
    private val driveIn: DriveInPlanner,
) : ViewModel() {

    init {
        // A snapshot left on disk with nothing running means the last session
        // died with the process: offer it back — or, when it is too old to
        // offer, just tidy up. A live session owns the file.
        viewModelScope.launch {
            if (repository.session.value !is MockSessionState.Idle) return@launch
            val snapshot = snapshotStore.read(System.currentTimeMillis())
            when {
                snapshot != null -> repository.setInterrupted(snapshot)
                snapshotStore.hasLeftover() -> discardInterrupted()
            }
        }
    }

    /** The stop playback is waiting at right now (an index into the user's route), with a whole-second countdown. */
    data class DwellInfo(val waypointIndex: Int, val secondsLeft: Int)

    val session = repository.session
    val playbackState = repository.state
    val latestFix = repository.latestFix
    val error = repository.error

    /** The drive in flight: what the engine drives and the user's route it plays; null between drives. */
    val drive: StateFlow<MockSessionRepository.LiveDrive?> = repository.liveDrive

    private val _preparing = MutableStateFlow(false)

    /** A drive-in is being routed; Start shows its spinner until the drive begins. */
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    /** Emits ~once per second during a dwell (whole-second changes only), else null. */
    val dwell: StateFlow<DwellInfo?> = repository.state
        .map { state ->
            // Engine indices count a drive-in's origin; the map's stops don't.
            val offset = repository.liveDrive.value?.stopOffset ?: 0
            (state as? PlaybackState.Dwelling)
                ?.takeIf { it.waypointIndex >= offset }
                ?.let { DwellInfo(it.waypointIndex - offset, ceil(it.waitSecondsLeft).toInt()) }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val speedMultiplier: StateFlow<Double> = repository.speedMultiplier

    /** The interrupted drive or hold on offer, until resumed, discarded, or overtaken by a new session. */
    val interrupted: StateFlow<SessionSnapshot?> = repository.interrupted

    /** How the last drive ended, set before the session leaves Playing. */
    val driveOutcome: StateFlow<DriveOutcome?> = repository.driveOutcome

    /**
     * [saved]: the route is in Saved routes as-is, so a map adopting the drive mid-way won't
     * offer Save. [from]: drive in from there first (a held spot, the real location) — the
     * lead-in is routed now and driven in front of [route], which itself stays as it is.
     */
    fun play(route: Route, profile: RoutingProfile, saved: Boolean = false, from: LatLng? = null) {
        if (from == null) {
            start(route, profile, saved, planned = null)
            return
        }
        if (_preparing.value) return
        viewModelScope.launch {
            _preparing.value = true
            val driven = try {
                driveIn.leadInto(route, from, profile)
            } finally {
                _preparing.value = false
            }
            start(driven, profile, saved, planned = route.takeIf { driven !== route })
        }
    }

    private fun start(route: Route, profile: RoutingProfile, saved: Boolean, planned: Route?) {
        repository.clearInterrupted()
        // Every new drive starts at real speed (Ethan, 2026-09-22): a 4× left over from
        // the last drive turned a walk into car speed inside a game. Resume keeps its own.
        repository.setSpeedMultiplier(1.0)
        repository.requestStart(route, profile, saved = saved, planned = planned)
        startService(Intent(context, MockSessionService::class.java).setAction(MockSessionService.ACTION_START))
    }

    /** Picks the interrupted session back up: the drive from where it stopped, or the hold at its spot. */
    fun resumeInterrupted() {
        val snapshot = repository.interrupted.value ?: return
        repository.clearInterrupted()
        val route = snapshot.route
        val holdPosition = snapshot.holdPosition
        when {
            snapshot.kind == SessionSnapshot.Kind.PLAYING && route != null -> {
                routeHandoff.set(route, snapshot.profile, saved = false)
                repository.setSpeedMultiplier(snapshot.speedMultiplier)
                repository.requestStart(
                    route,
                    snapshot.profile,
                    SimulationEngine.ResumePoint(snapshot.distanceMeters, snapshot.dwellSecondsLeft),
                )
                startService(
                    Intent(context, MockSessionService::class.java).setAction(MockSessionService.ACTION_START),
                )
            }
            holdPosition != null -> hold(holdPosition)
            else -> snapshotStore.clear()
        }
    }

    /**
     * Forgets the interrupted session. A process killed mid-session (force
     * stop, OOM) never ran the service's teardown, so its test providers can
     * still be registered and freezing the device on the last fake fix: the
     * service's release path is asked to remove them, keeping mock ownership
     * in one place.
     */
    fun discardInterrupted() {
        repository.clearInterrupted()
        snapshotStore.clear()
        release()
    }

    /** Hold the mocked location at one spot (long-press pin). */
    fun hold(position: LatLng) {
        repository.clearInterrupted()
        startService(
            Intent(context, MockSessionService::class.java)
                .setAction(MockSessionService.ACTION_HOLD)
                .putExtra(MockSessionService.EXTRA_LAT, position.latitude)
                .putExtra(MockSessionService.EXTRA_LNG, position.longitude),
        )
    }

    /** Full release: removes test providers, returning the device to its real location. */
    fun release() {
        // Plain startService: the app is foregrounded when the banner is tapped, and
        // startForegroundService would oblige a startForeground call the release path never makes.
        context.startService(
            Intent(context, MockSessionService::class.java).setAction(MockSessionService.ACTION_RELEASE),
        )
    }

    /** Thumbstick: move the held location; the repository ignores it unless holding. */
    fun nudgeHold(position: LatLng) = repository.requestHoldMove(position)

    fun pause() = repository.pause()

    fun resume() = repository.resume()

    fun stopPlayback() = repository.stop()

    fun setSpeedMultiplier(multiplier: Double) = repository.setSpeedMultiplier(multiplier)

    /**
     * Live-updates one stop's wait on the running engine; a no-op between drives.
     * [waypointIndex] is the user's stop; a drive-in shifts the engine's count by one.
     */
    fun setWaypointWait(waypointIndex: Int, waitSeconds: Int) {
        val offset = repository.liveDrive.value?.stopOffset ?: 0
        repository.setWaypointWait(waypointIndex + offset, waitSeconds)
    }

    /** Ends the wait the drive is in right now (the band's Skip wait); the stop's saved wait is untouched. */
    fun skipWait() {
        val dwelling = repository.state.value as? PlaybackState.Dwelling ?: return
        repository.setWaypointWait(dwelling.waypointIndex, 0)
    }

    fun consumeError() = repository.consumeError()

    /** The user declined the location permission a mock needs: say so, like any other session error. */
    fun reportPermissionDenied() = repository.reportError(context.getString(R.string.snack_location_permission))

    /** Declined the permission "Go to my location" needs. */
    fun reportLocatePermissionDenied() = repository.reportError(context.getString(R.string.snack_locate_permission))

    /** The one-shot real-location read came back empty (GPS cold, indoors). */
    fun reportLocateFailed() = repository.reportError(context.getString(R.string.snack_locate_failed))

    private fun startService(intent: Intent) {
        ContextCompat.startForegroundService(context, intent)
    }
}
