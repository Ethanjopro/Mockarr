package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.di.USER_AGENT
import dev.mockarr.core.data.MockarrSettings
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.routing.OsrmRouteProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    sealed interface TestState {
        data object Idle : TestState
        data object Testing : TestState
        data object Success : TestState
        data class Failure(val message: String) : TestState
    }

    val settings = repository.settings

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    fun applyOsrmBaseUrl(url: String) {
        _testState.value = TestState.Idle
        viewModelScope.launch { repository.setOsrmBaseUrl(url) }
    }

    fun applyTileStyleUrl(url: String) {
        viewModelScope.launch { repository.setTileStyleUrl(url) }
    }

    fun setTickHz(value: Double) {
        viewModelScope.launch { repository.setTickHz(value) }
    }

    fun setJitterEnabled(value: Boolean) {
        viewModelScope.launch { repository.setJitterEnabled(value) }
    }

    fun setJitterSigmaMeters(value: Double) {
        viewModelScope.launch { repository.setJitterSigmaMeters(value) }
    }

    fun setDefaultProfile(profile: RoutingProfile) {
        viewModelScope.launch { repository.setDefaultProfile(profile) }
    }

    fun setUnits(units: DistanceUnits) {
        viewModelScope.launch { repository.setUnits(units) }
    }

    /** Fires a tiny fixed route request against the given server. */
    fun testConnection(url: String) {
        _testState.value = TestState.Testing
        viewModelScope.launch {
            val provider = OsrmRouteProvider(
                baseUrlProvider = { MockarrSettings.normalizeBaseUrl(url) },
                userAgent = USER_AGENT,
            )
            provider.route(TEST_WAYPOINTS, RoutingProfile.DRIVING).fold(
                onSuccess = { _testState.value = TestState.Success },
                onFailure = { error ->
                    _testState.value = TestState.Failure(error.message ?: "Unknown error")
                },
            )
        }
    }

    private companion object {
        // A short hop in central Berlin — resolvable by any worldwide OSRM install
        val TEST_WAYPOINTS = listOf(LatLng(52.5170, 13.3888), LatLng(52.5206, 13.4098))
    }
}
