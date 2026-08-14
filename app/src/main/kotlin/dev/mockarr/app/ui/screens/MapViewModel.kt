package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.ui.RouteHandoff
import dev.mockarr.app.ui.map.CameraCommand
import dev.mockarr.core.data.SavedRoutesRepository
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.MapCamera
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.routing.GeocodingResult
import dev.mockarr.core.routing.PhotonGeocoder
import dev.mockarr.core.routing.RouteProvider
import dev.mockarr.core.routing.RoutingException
import dev.mockarr.core.routing.StraightLineRouteProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    private val routeProvider: RouteProvider,
    private val geocoder: PhotonGeocoder,
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
        val savedConfirmation: String? = null,
    )

    data class SearchState(
        val query: String = "",
        val searching: Boolean = false,
        val results: List<GeocodingResult> = emptyList(),
        val errorMessage: String? = null,
    )

    private val straightLine = StraightLineRouteProvider()
    private val _uiState = MutableStateFlow(
        UiState(profile = settingsRepository.settings.value.defaultProfile),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _cameraCommand = MutableStateFlow<CameraCommand?>(null)
    val cameraCommand: StateFlow<CameraCommand?> = _cameraCommand.asStateFlow()

    val tileStyleUrl: StateFlow<String> = settingsRepository.settings
        .map { it.tileStyleUrl }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsRepository.settings.value.tileStyleUrl,
        )

    val customServerConfigured: StateFlow<Boolean> = settingsRepository.settings
        .map { it.customServerConfigured }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsRepository.settings.value.customServerConfigured,
        )

    val units: StateFlow<DistanceUnits> = settingsRepository.settings
        .map { it.units }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsRepository.settings.value.units,
        )

    val map3dEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.map3dEnabled }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsRepository.settings.value.map3dEnabled,
        )

    /** Whether the camera tracks the playback dot; a user pan turns it off. */
    private val _followCamera = MutableStateFlow(true)
    val followCamera: StateFlow<Boolean> = _followCamera.asStateFlow()

    private var routeJob: Job? = null
    private var searchJob: Job? = null
    private var cameraSeq = 0L
    private var profileTouched = false
    private var lastKnownCamera: MapCamera? = null
    private val cameraSaves = MutableStateFlow<MapCamera?>(null)

    init {
        viewModelScope.launch {
            routeHandoff.pending.collect { loaded ->
                if (loaded != null) {
                    routeHandoff.clear()
                    loadSavedRoute(loaded.route, loaded.profile)
                }
            }
        }
        // The default profile loads from DataStore after construction — follow
        // it until the user picks a profile by hand.
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.defaultProfile }
                .distinctUntilChanged()
                .collect { profile ->
                    if (!profileTouched && _uiState.value.profile != profile) {
                        _uiState.update { it.copy(profile = profile) }
                        scheduleRouteFetch()
                    }
                }
        }
        // Camera idles arrive on every gesture end; debounce so each pan
        // doesn't cost a DataStore file rewrite.
        viewModelScope.launch {
            cameraSaves.filterNotNull().collectLatest { camera ->
                delay(CAMERA_SAVE_DEBOUNCE_MILLIS)
                settingsRepository.setLastCamera(camera)
            }
        }
        // Seed the search bias so pre-pan queries still rank nearby places first.
        viewModelScope.launch {
            val saved = settingsRepository.awaitLoaded().lastCamera
            if (lastKnownCamera == null) lastKnownCamera = saved
        }
    }

    fun addWaypoint(point: LatLng) {
        _uiState.update { it.copy(waypoints = it.waypoints + point) }
        scheduleRouteFetch()
    }

    fun undoWaypoint() {
        if (_uiState.value.waypoints.isEmpty()) return
        _uiState.update { it.copy(waypoints = it.waypoints.dropLast(1)) }
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
        profileTouched = true
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

    /** One-shot cold-start camera restore, read straight from disk (no default-value race). */
    suspend fun initialCamera(): MapCamera? = settingsRepository.awaitLoaded().lastCamera

    fun saveCamera(camera: MapCamera) {
        lastKnownCamera = camera
        cameraSaves.value = camera
    }

    fun setFollowCamera(follow: Boolean) {
        _followCamera.value = follow
    }

    fun toggleMap3d() {
        viewModelScope.launch {
            settingsRepository.setMap3dEnabled(!settingsRepository.settings.value.map3dEnabled)
        }
    }

    /** As-you-type: debounce, cancel the in-flight lookup, bias results toward the camera. */
    fun setSearchQuery(query: String) {
        _search.update { it.copy(query = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _search.update { it.copy(searching = false, results = emptyList(), errorMessage = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            runSearch(trimmed)
        }
    }

    /** IME search action — skip the debounce. */
    fun submitSearch() {
        val query = _search.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    private suspend fun runSearch(query: String) {
        _search.update { it.copy(searching = true, errorMessage = null) }
        geocoder.search(query, bias = lastKnownCamera?.target).fold(
            onSuccess = { results ->
                _search.update {
                    it.copy(
                        searching = false,
                        results = results,
                        errorMessage = if (results.isEmpty()) "No places found" else null,
                    )
                }
            },
            onFailure = { error ->
                _search.update {
                    it.copy(
                        searching = false,
                        errorMessage = "Search failed: ${error.message ?: "network error"}",
                    )
                }
            },
        )
    }

    fun selectSearchResult(result: GeocodingResult) {
        _search.update { it.copy(results = emptyList(), errorMessage = null) }
        _cameraCommand.value = CameraCommand(result.position, SEARCH_ZOOM, seq = cameraSeq++)
    }

    fun clearSearchResults() {
        _search.update { it.copy(results = emptyList(), errorMessage = null) }
    }

    private fun loadSavedRoute(route: Route, profile: RoutingProfile) {
        routeJob?.cancel()
        profileTouched = true
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
        const val CAMERA_SAVE_DEBOUNCE_MILLIS = 1_000L
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val MIN_QUERY_LENGTH = 3
        const val SEARCH_ZOOM = 14.0
    }
}
