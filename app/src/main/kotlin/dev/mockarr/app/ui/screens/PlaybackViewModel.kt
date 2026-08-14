package dev.mockarr.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mockarr.app.playback.PlaybackService
import dev.mockarr.app.playback.PlaybackSessionRepository
import dev.mockarr.core.model.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlaybackSessionRepository,
) : ViewModel() {

    val playbackState = repository.state
    val latestFix = repository.latestFix
    val error = repository.error

    private val _speedMultiplier = MutableStateFlow(1.0)
    val speedMultiplier: StateFlow<Double> = _speedMultiplier.asStateFlow()

    fun play(route: Route) {
        repository.requestStart(route)
        val intent = Intent(context, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun pause() = repository.pause()

    fun resume() = repository.resume()

    fun stopPlayback() = repository.stop()

    fun setSpeedMultiplier(multiplier: Double) {
        _speedMultiplier.value = multiplier
        repository.setSpeedMultiplier(multiplier)
    }

    fun consumeError() = repository.consumeError()
}
