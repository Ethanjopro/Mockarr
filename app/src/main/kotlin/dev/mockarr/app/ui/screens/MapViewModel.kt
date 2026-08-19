package dev.mockarr.app.ui.screens

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.playback.MockSessionRepository
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.app.ui.RouteHandoff
import dev.mockarr.app.ui.map.CameraCommand
import dev.mockarr.core.data.SavedRoutesRepository
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.MapCamera
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.Waypoint
import dev.mockarr.core.routing.ElevationSampling
import dev.mockarr.core.routing.GeocodingResult
import dev.mockarr.core.routing.OpenMeteoElevationClient
import dev.mockarr.core.routing.PhotonGeocoder
import dev.mockarr.core.routing.RouteProvider
import dev.mockarr.core.routing.RoutingException
import dev.mockarr.core.routing.StraightLineRouteProvider
import dev.mockarr.core.simulation.TrafficModel
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.coroutines.resume

@HiltViewModel
class MapViewModel @Inject constructor(
    private val routeProvider: RouteProvider,
    private val geocoder: PhotonGeocoder,
    private val elevationClient: OpenMeteoElevationClient,
    private val locationManager: LocationManager,
    private val sessionRepository: MockSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val savedRoutesRepository: SavedRoutesRepository,
    private val routeHandoff: RouteHandoff,
) : ViewModel() {

    data class UiState(
        val waypoints: List<Waypoint> = emptyList(),
        val profile: RoutingProfile = RoutingProfile.DRIVING,
        val route: Route? = null,
        val routeIsFallback: Boolean = false,
        val isRouting: Boolean = false,
        val errorMessage: String? = null,
        val savedConfirmation: String? = null,
        /** Congestion preview multiplier for the "about N min" summary. */
        val trafficFactor: Double = 1.0,
    )

    data class SearchSuggestion(
        val result: GeocodingResult,
        val distanceMeters: Double?,
    )

    data class SearchState(
        val query: String = "",
        val searching: Boolean = false,
        val results: List<SearchSuggestion> = emptyList(),
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

    /** Index of the waypoint whose context menu is open, or null. */
    private val _selectedWaypoint = MutableStateFlow<Int?>(null)
    val selectedWaypoint: StateFlow<Int?> = _selectedWaypoint.asStateFlow()

    /** Suggested name for the Save dialog ("X to Y, City"), null until resolved. */
    private val _suggestedName = MutableStateFlow<String?>(null)
    val suggestedName: StateFlow<String?> = _suggestedName.asStateFlow()

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
    private var elevationJob: Job? = null
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
        // Keep the summary's traffic preview in sync with the setting toggle.
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.trafficSimEnabled }
                .distinctUntilChanged()
                .collect { _uiState.update { s -> s.copy(trafficFactor = settingsRepository.currentTrafficFactor()) } }
        }
    }

    fun addWaypoint(point: LatLng) {
        _selectedWaypoint.value = null
        _uiState.update { it.copy(waypoints = it.waypoints + Waypoint(point)) }
        scheduleRouteFetch()
    }

    /** Insert a new route origin (e.g. the held position, chosen at Play time). */
    fun prependWaypoint(point: LatLng) {
        _selectedWaypoint.value = null
        _uiState.update { it.copy(waypoints = listOf(Waypoint(point)) + it.waypoints) }
        scheduleRouteFetch()
    }

    fun undoWaypoint() {
        if (_uiState.value.waypoints.isEmpty()) return
        _selectedWaypoint.value = null
        _uiState.update { it.copy(waypoints = it.waypoints.dropLast(1)) }
        scheduleRouteFetch()
    }

    fun clearWaypoints() {
        routeJob?.cancel()
        elevationJob?.cancel()
        _selectedWaypoint.value = null
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

    fun removeWaypoint(index: Int) {
        if (index !in _uiState.value.waypoints.indices) return
        _selectedWaypoint.value = null
        _uiState.update { state ->
            state.copy(waypoints = state.waypoints.filterIndexed { i, _ -> i != index })
        }
        scheduleRouteFetch()
    }

    /**
     * Sets (or clears, with 0) the dwell at one stop. No route refetch — the
     * geometry is unchanged; the in-hand route is patched so an immediate Play
     * carries the wait.
     */
    fun setWaypointWait(index: Int, waitSeconds: Int) {
        if (index !in _uiState.value.waypoints.indices) return
        _selectedWaypoint.value = null
        _uiState.update { state ->
            val updated = state.waypoints.mapIndexed { i, waypoint ->
                if (i == index) waypoint.copy(waitSeconds = waitSeconds) else waypoint
            }
            state.copy(waypoints = updated, route = state.route?.withWaits(updated))
        }
    }

    /** Marker tapped on the map (or null to dismiss the waypoint menu). */
    fun selectWaypoint(index: Int?) {
        _selectedWaypoint.value = index
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

    /** Kicks off "«start» to «end», «city»" naming for the Save dialog. */
    fun requestNameSuggestion() {
        val route = _uiState.value.route ?: return
        _suggestedName.value = null
        viewModelScope.launch {
            val start = geocoder.reverse(route.points.first()).getOrNull()
            val end = geocoder.reverse(route.points.last()).getOrNull()
            val startName = start?.name ?: return@launch
            val endName = end?.name ?: return@launch
            val citySuffix = end.city?.let { ", $it" }.orEmpty()
            _suggestedName.value = "$startName to $endName$citySuffix"
        }
    }

    /** Pans the camera (never touches mock state). */
    fun panTo(position: LatLng) {
        _cameraCommand.value = CameraCommand(position, LOCATE_ZOOM, seq = cameraSeq++)
    }

    /** Pans to the device's REAL location; requires fine-location permission. */
    fun locateReal() {
        viewModelScope.launch {
            // Two-stage: jump to the cached fix instantly, refine once a fresh
            // fix arrives and only if it's meaningfully different.
            val cached = locationManager.quickLastKnown()
                ?.let { LatLng(it.latitude, it.longitude) }
                ?.also(::panTo)
            val fresh = locationManager.currentReal()?.let { LatLng(it.latitude, it.longitude) }
                ?: return@launch
            if (cached == null || GeoMath.distanceMeters(cached, fresh) > LOCATE_REFINE_METERS) {
                panTo(fresh)
            }
        }
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
        // Ranking anchor: mocked position first, then real location, then the camera.
        val bias = sessionRepository.mockedPosition()
            ?: locationManager.quickLastKnown()?.let { LatLng(it.latitude, it.longitude) }
            ?: lastKnownCamera?.target
        geocoder.search(query, bias = bias).fold(
            onSuccess = { results ->
                val suggestions = results.map { result ->
                    SearchSuggestion(
                        result = result,
                        distanceMeters = bias?.let { GeoMath.distanceMeters(it, result.position) },
                    )
                }
                _search.update {
                    it.copy(
                        searching = false,
                        results = suggestions,
                        errorMessage = if (suggestions.isEmpty()) "No places found" else null,
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
        val anchors = route.snappedWaypoints
            .ifEmpty { listOf(route.points.first(), route.points.last()) }
        val waits = route.waypointWaitsSeconds
        _uiState.update {
            it.copy(
                waypoints = anchors.mapIndexed { i, p -> Waypoint(p, waits.getOrElse(i) { 0 }) },
                profile = profile,
                route = route,
                routeIsFallback = false,
                isRouting = false,
                errorMessage = null,
                trafficFactor = settingsRepository.currentTrafficFactor(),
            )
        }
        enrichWithElevations(route)
    }

    /** Fetches terrain elevations for [route] and attaches them once resolved. */
    private fun enrichWithElevations(route: Route) {
        if (route.altitudes != null) return
        elevationJob?.cancel()
        elevationJob = viewModelScope.launch {
            val indices = ElevationSampling.sampleIndices(route.points)
            val sampled = elevationClient.elevations(indices.map { route.points[it] })
                .getOrNull() ?: return@launch // offline/unavailable → constant altitude fallback
            val altitudes = ElevationSampling.interpolate(route.points, indices, sampled)
            // Identity guard: don't attach a stale profile to a newer route.
            _uiState.update { state ->
                if (state.route === route) {
                    state.copy(route = route.copy(altitudes = altitudes))
                } else {
                    state
                }
            }
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
            val requestWaypoints = current.waypoints
            val positions = requestWaypoints.map { it.position }
            routeProvider.route(positions, current.profile).fold(
                onSuccess = { fetched ->
                    val route = fetched.withWaits(requestWaypoints)
                    _uiState.update {
                        it.copy(
                            route = route,
                            routeIsFallback = false,
                            isRouting = false,
                            trafficFactor = settingsRepository.currentTrafficFactor(),
                        )
                    }
                    enrichWithElevations(route)
                },
                onFailure = { error ->
                    val fallback = straightLine.route(positions, current.profile)
                        .getOrNull()
                        ?.withWaits(requestWaypoints)
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

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
        const val CAMERA_SAVE_DEBOUNCE_MILLIS = 1_000L
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val MIN_QUERY_LENGTH = 3
        const val SEARCH_ZOOM = 16.0
        const val LOCATE_ZOOM = 15.0
        const val LOCATE_REFINE_METERS = 50.0
    }
}

private const val LOCATE_TIMEOUT_MILLIS = 5_000L

// Permission is gated by the UI before callers reach this.
@SuppressLint("MissingPermission")
private fun LocationManager.quickLastKnown(): Location? =
    runCatching { getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        ?: runCatching { getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()

// Permission is gated by the UI before callers reach this.
@SuppressLint("MissingPermission")
private suspend fun LocationManager.currentReal(): Location? {
    val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        withTimeoutOrNull(LOCATE_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                runCatching {
                    getCurrentLocation(
                        LocationManager.GPS_PROVIDER,
                        null,
                        { runnable -> runnable.run() },
                    ) { location -> continuation.resume(location) }
                }.onFailure { continuation.resume(null) }
            }
        }
    } else {
        null
    }
    return current ?: quickLastKnown()
}

/** The position the device currently mocks, or null when idle. */
private fun MockSessionRepository.mockedPosition(): LatLng? =
    when (val current = session.value) {
        is MockSessionState.Holding -> current.position
        is MockSessionState.Playing -> latestFix.value?.position
        else -> null
    }

private fun SettingsRepository.currentTrafficFactor(): Double =
    if (settings.value.trafficSimEnabled) {
        val now = LocalDateTime.now()
        TrafficModel.congestionFactor(now.dayOfWeek, now.hour, now.minute)
    } else {
        1.0
    }

private fun friendlyMessage(error: Throwable): String {
    val base = when (error) {
        is RoutingException -> error.message ?: "Routing failed"
        else -> "Routing failed: ${error.message ?: "unknown error"}"
    }
    return "$base — showing straight line instead"
}

/**
 * Waypoints to draw on the map: the router's road-snapped locations when they
 * match the current request (so markers meet the route line), raw taps
 * otherwise. The size guard covers fetches still in flight after a new tap.
 */
internal fun displayWaypoints(
    waypoints: List<Waypoint>,
    route: Route?,
    routeIsFallback: Boolean,
): List<Waypoint> =
    if (route != null && !routeIsFallback && route.snappedWaypoints.size == waypoints.size) {
        waypoints.zip(route.snappedWaypoints) { waypoint, snapped -> waypoint.copy(position = snapped) }
    } else {
        waypoints
    }

/** Attaches per-waypoint waits to a route when counts align; otherwise unchanged. */
internal fun Route.withWaits(waypoints: List<Waypoint>): Route =
    if (waypoints.size == legs.size + 1) {
        copy(waypointWaitsSeconds = waypoints.map { it.waitSeconds })
    } else {
        this
    }
