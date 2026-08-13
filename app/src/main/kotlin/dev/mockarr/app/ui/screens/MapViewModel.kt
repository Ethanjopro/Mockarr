package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.ui.RouteHandoff
import dev.mockarr.core.data.SavedRoutesRepository
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.routing.RouteProvider
import dev.mockarr.core.routing.RoutingException
import dev.mockarr.core.routing.StraightLineRouteProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val routeProvider: RouteProvider,
    private val settingsRepository: SettingsRepository,
    private val savedRoutesRepository: SavedRoutesRepository,
    private val routeHandoff: RouteHandoff,
) : ViewModel() {

    data class UiState(
        val waypoints: List<LatLng> = emptyList(),
        val profile: RoutingProfile = RoutingProfile.DRIVING,
        val route: Route? = null,
        val routeIsFallback: Boolean = false,
        val isRouting: Boolean = false,
        val errorMessage: String? = null,
        val customServerConfigured: Boolean = false,
        val savedConfirmation: String? = null,
    )

    private val straightLine = StraightLineRouteProvider()
    private val _uiState = MutableStateFlow(
        UiState(profile = settingsRepository.settings.value.defaultProfile),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val tileStyleUrl: StateFlow<String> = settingsRepository.settings
        .map { it.tileStyleUrl }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsRepository.settings.value.tileStyleUrl,
        )

    private var routeJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(customServerConfigured = settings.customServerConfigured) }
            }
        }
        viewModelScope.launch {
            routeHandoff.pending.collect { loaded ->
                if (loaded != null) {
                    routeHandoff.clear()
                    loadSavedRoute(loaded.route, loaded.profile)
                }
            }
        }
    }

    fun addWaypoint(point: LatLng) {
        _uiState.update { it.copy(waypoints = it.waypoints + point) }
        scheduleRouteFetch()
    }

    fun clearWaypoints() {
        routeJob?.cancel()
        _uiState.update {
            it.copy(
                waypoints = emptyList(),
                route = null,
                routeIsFallback = false,
                isRouting = false,
                errorMessage = null,
            )
        }
    }

    fun setProfile(profile: RoutingProfile) {
        if (_uiState.value.profile == profile) return
        _uiState.update { it.copy(profile = profile) }
        scheduleRouteFetch()
    }

    fun saveRoute(name: String) {
        val state = _uiState.value
        val route = state.route ?: return
        viewModelScope.launch {
            savedRoutesRepository.save(
                name = name.ifBlank { "Unnamed route" },
                route = route,
                profile = state.profile,
                nowEpochMillis = System.currentTimeMillis(),
            )
            _uiState.update { it.copy(savedConfirmation = "Saved \"$name\"") }
        }
    }

    fun consumeSavedConfirmation() {
        _uiState.update { it.copy(savedConfirmation = null) }
    }

    private fun loadSavedRoute(route: Route, profile: RoutingProfile) {
        routeJob?.cancel()
        _uiState.update {
            it.copy(
                waypoints = listOf(route.points.first(), route.points.last()),
                profile = profile,
                route = route,
                routeIsFallback = false,
                isRouting = false,
                errorMessage = null,
            )
        }
    }

    private fun scheduleRouteFetch() {
        routeJob?.cancel()
        val state = _uiState.value
        if (state.waypoints.size < 2) {
            _uiState.update { it.copy(route = null, routeIsFallback = false, errorMessage = null) }
            return
        }
        routeJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            _uiState.update { it.copy(isRouting = true, errorMessage = null) }
            val current = _uiState.value
            routeProvider.route(current.waypoints, current.profile).fold(
                onSuccess = { route ->
                    _uiState.update {
                        it.copy(route = route, routeIsFallback = false, isRouting = false)
                    }
                },
                onFailure = { error ->
                    val fallback = straightLine.route(current.waypoints, current.profile).getOrNull()
                    _uiState.update {
                        it.copy(
                            route = fallback,
                            routeIsFallback = true,
                            isRouting = false,
                            errorMessage = friendlyMessage(error),
                        )
                    }
                },
            )
        }
    }

    private fun friendlyMessage(error: Throwable): String {
        val base = when (error) {
            is RoutingException -> error.message ?: "Routing failed"
            else -> "Routing failed: ${error.message ?: "unknown error"}"
        }
        return "$base — showing straight line instead"
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
    }
}
