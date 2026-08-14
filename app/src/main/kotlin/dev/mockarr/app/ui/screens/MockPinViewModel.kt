package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.ui.errorMessageOrNull
import dev.mockarr.core.mocklocation.MockLocationController
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
 * Pins the mocked location to a fixed coordinate at 1 Hz ("teleport" mode,
 * triggered by long-pressing the map). Route playback arrives in M3.
 */
@HiltViewModel
class MockPinViewModel @Inject constructor(
    private val controller: MockLocationController,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Mocking(val position: LatLng) : UiState
        data class Error(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var ticker: Job? = null

    fun startMocking(position: LatLng) {
        val error = controller.start().errorMessageOrNull()
        if (error != null) {
            _uiState.value = UiState.Error(error)
            return
        }
        ticker?.cancel()
        val fix = SimulatedFix(
            position = position,
            speedMetersPerSecond = 0.0,
            bearingDegrees = 0.0,
            accuracyMeters = PIN_ACCURACY_METERS,
            altitudeMeters = PIN_ALTITUDE_METERS,
        )
        ticker = viewModelScope.launch {
            while (isActive) {
                controller.push(fix)
                delay(TICK_MILLIS)
            }
        }
        _uiState.value = UiState.Mocking(position)
    }

    fun stopMocking() {
        ticker?.cancel()
        ticker = null
        controller.stop()
        _uiState.value = UiState.Idle
    }

    fun dismissError() {
        if (_uiState.value is UiState.Error) {
            _uiState.value = UiState.Idle
        }
    }

    override fun onCleared() {
        stopMocking()
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val PIN_ACCURACY_METERS = 5.0
        const val PIN_ALTITUDE_METERS = 35.0
    }
}
