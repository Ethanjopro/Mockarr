package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.core.mocklocation.MockLocationController
import dev.mockarr.core.mocklocation.MockStartResult
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.SimulatedFix
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * M1 walking skeleton: pins the mocked location to a fixed coordinate at 1 Hz.
 * Replaced by route playback (and a foreground service) in M3.
 */
@HiltViewModel
class MockPinViewModel @Inject constructor(
    private val controller: MockLocationController,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Mocking : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var ticker: Job? = null

    fun startMocking() {
        when (val result = controller.start()) {
            MockStartResult.Ok -> {
                ticker = viewModelScope.launch {
                    while (isActive) {
                        controller.push(PIN_FIX)
                        delay(TICK_MILLIS)
                    }
                }
                _uiState.value = UiState.Mocking
            }
            MockStartResult.NotSelectedAsMockApp -> {
                _uiState.value = UiState.Error(
                    "Mockarr isn't selected as the mock location app yet — " +
                        "open the Setup checklist below.",
                )
            }
            is MockStartResult.ProviderError -> {
                _uiState.value = UiState.Error(result.message)
            }
        }
    }

    fun stopMocking() {
        ticker?.cancel()
        ticker = null
        controller.stop()
        _uiState.value = UiState.Idle
    }

    override fun onCleared() {
        stopMocking()
    }

    companion object {
        /** Eiffel Tower — unmistakable in Google Maps. */
        val PIN_POSITION = LatLng(48.8584, 2.2945)

        private val PIN_FIX = SimulatedFix(
            position = PIN_POSITION,
            speedMetersPerSecond = 0.0,
            bearingDegrees = 0.0,
            accuracyMeters = 5.0,
            altitudeMeters = 35.0,
        )
        private const val TICK_MILLIS = 1_000L
    }
}
