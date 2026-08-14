package dev.mockarr.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mockarr.app.playback.MockSessionRepository
import dev.mockarr.app.playback.MockSessionService
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MockSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MockSessionRepository,
) : ViewModel() {

    val session = repository.session
    val playbackState = repository.state
    val latestFix = repository.latestFix
    val error = repository.error

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
