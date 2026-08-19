package dev.mockarr.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mockarr.app.playback.MockSessionRepository
import dev.mockarr.app.playback.MockSessionService
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.ceil

@HiltViewModel
class MockSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MockSessionRepository,
) : ViewModel() {

    /** The stop playback is waiting at right now, with a whole-second countdown. */
    data class DwellInfo(val waypointIndex: Int, val secondsLeft: Int)

    val session = repository.session
    val playbackState = repository.state
    val latestFix = repository.latestFix
    val error = repository.error

    /** Emits ~once per second during a dwell (whole-second changes only), else null. */
    val dwell: StateFlow<DwellInfo?> = repository.state
        .map { state ->
            (state as? PlaybackState.Dwelling)
                ?.takeIf { it.waypointIndex >= 0 }
                ?.let { DwellInfo(it.waypointIndex, ceil(it.waitSecondsLeft).toInt()) }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val _speedMultiplier = MutableStateFlow(1.0)
    val speedMultiplier: StateFlow<Double> = _speedMultiplier.asStateFlow()

    fun play(route: Route) {
        repository.requestStart(route)
        startService(Intent(context, MockSessionService::class.java).setAction(MockSessionService.ACTION_START))
    }

    /** Hold the mocked location at one spot (long-press pin). */
    fun hold(position: LatLng) {
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

    fun setSpeedMultiplier(multiplier: Double) {
        _speedMultiplier.value = multiplier
        repository.setSpeedMultiplier(multiplier)
    }

    fun consumeError() = repository.consumeError()

    private fun startService(intent: Intent) {
        ContextCompat.startForegroundService(context, intent)
    }
}
