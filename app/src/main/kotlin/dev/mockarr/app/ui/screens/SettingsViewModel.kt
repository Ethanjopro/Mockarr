package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.routing.BackendConfig
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    backend: BackendConfig,
) : ViewModel() {

    /** The build carries a managed-backend key (ADR 0003): walking/cycling and the credits line follow it. */
    val managedAvailable: Boolean = backend.managedAvailable

    val settings = repository.settings

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

    fun setStayAtDestination(value: Boolean) {
        viewModelScope.launch { repository.setStayAtDestination(value) }
    }

    fun setTrafficSimEnabled(value: Boolean) {
        viewModelScope.launch { repository.setTrafficSimEnabled(value) }
    }

    fun setOffRoadEnabled(value: Boolean) {
        viewModelScope.launch { repository.setOffRoadEnabled(value) }
    }

    fun setOffRoadWalkEnabled(value: Boolean) {
        viewModelScope.launch { repository.setOffRoadWalkEnabled(value) }
    }
}
