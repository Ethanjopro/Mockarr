package dev.mockarr.app.ui.screens

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import dev.mockarr.core.routing.BackendConfig
import dev.mockarr.core.routing.ElevationProvider
import dev.mockarr.core.routing.ElevationSampling
import dev.mockarr.core.routing.Geocoder
import dev.mockarr.core.routing.GeocodingResult
import dev.mockarr.core.routing.RouteProvider
import dev.mockarr.core.routing.RoutingException
import dev.mockarr.core.routing.StraightLineRouteProvider
import dev.mockarr.core.routing.stitchOffRoad
import dev.mockarr.core.simulation.TrafficModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.coroutines.resume

@HiltViewModel
class MapViewModel @Inject constructor(
    private val routeProvider: RouteProvider,
    private val geocoder: Geocoder,
    private val elevationClient: ElevationProvider,
    private val locationManager: LocationManager,
    private val settingsRepository: SettingsRepository,
    private val savedRoutesRepository: SavedRoutesRepository,
    private val routeHandoff: RouteHandoff,
    backend: BackendConfig,
) : ViewModel() {

    data class UiState(
        val waypoints: List<Waypoint> = emptyList(),
        val profile: RoutingProfile = RoutingProfile.DRIVING,
        val route: Route? = null,
        val routeIsFallback: Boolean = false,
        /**
         * The stop positions [route] was fetched for — the proof a snapped
         * position belongs to a stop. Undo/redo swap [waypoints] while the old
         * route waits out the refetch debounce; without this, markers rendered
         * at the stale route's snapped spots (the "random jump", session 25).
         */
        val routedFor: List<LatLng>? = null,
        val isRouting: Boolean = false,
        val routingError: RoutingError? = null,
        /** The name just saved under, for the confirmation; consumed by the screen. */
        val savedName: String? = null,
        /** The loaded route exists in Saved routes as-is; any edit clears it. */
        val routeSaved: Boolean = false,
        /** Congestion preview multiplier for the "about N min" summary. */
        val trafficFactor: Double = 1.0,
    )

    private val straightLine = StraightLineRouteProvider()
    private val _uiState = MutableStateFlow(
        UiState(profile = settingsRepository.settings.value.defaultProfile),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _cameraCommand = MutableStateFlow<CameraCommand?>(null)
    val cameraCommand: StateFlow<CameraCommand?> = _cameraCommand.asStateFlow()

    /** Selection, pending Move and Start choice — reset on every stop edit. */
    val interaction = MapInteraction { it in _uiState.value.waypoints.indices }

    private val _builderMode = MutableStateFlow(false)
    val builderMode: StateFlow<Boolean> = _builderMode.asStateFlow()

    /**
     * The last search pick, pinned on the map until it becomes a stop, a new pick replaces it,
     * or the builder closes.
     */
    private val _searchedPlace = MutableStateFlow<GeocodingResult?>(null)
    val searchedPlace: StateFlow<GeocodingResult?> = _searchedPlace.asStateFlow()

    val tileStyleUrl: StateFlow<String> = settingsRepository.settings
        .map { it.tileStyleUrl }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsRepository.settings.value.tileStyleUrl,
        )

    /** Walking/cycling available: a custom OSRM, or the managed backend (ADR 0003). */
    val profilesUnlocked: StateFlow<Boolean> = settingsRepository.settings
        .map { it.profilesUnlocked(backend.managedAvailable) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsRepository.settings.value.profilesUnlocked(backend.managedAvailable),
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
    private var elevationJob: Job? = null
    private var cameraSeq = 0L
    private var profileTouched = false

    // Live camera (every move frame) for the thumbstick's zoom; idle camera for DataStore.
    private val liveCamera = MutableStateFlow<MapCamera?>(null)
    private val cameraSaves = MutableStateFlow<MapCamera?>(null)

    /** Latest camera (target + zoom), live during gestures and animations. */
    val camera: StateFlow<MapCamera?> = liveCamera.asStateFlow()

    private val history = BuilderHistory()
    val canUndo: StateFlow<Boolean> = history.canUndo
    val canRedo: StateFlow<Boolean> = history.canRedo

    init {
        viewModelScope.launch {
            routeHandoff.pending.collect { loaded ->
                if (loaded != null) {
                    routeHandoff.clear()
                    // A saved route arrives loaded, not built: builder closed,
                    // history gone, camera framing it (was loadSavedRoute).
                    _builderMode.value = false
                    _searchedPlace.value = null
                    history.clear()
                    routeJob?.cancel()
                    profileTouched = true
                    val route = loaded.route
                    val anchors = route.snappedWaypoints
                        .ifEmpty { listOf(route.points.first(), route.points.last()) }
                    val waits = route.waypointWaitsSeconds
                    _uiState.update {
                        it.copy(
                            waypoints = anchors.mapIndexed { i, p -> Waypoint(p, waits.getOrElse(i) { 0 }) },
                            profile = loaded.profile,
                            route = route,
                            routeIsFallback = false,
                            routedFor = anchors,
                            isRouting = false,
                            routingError = null,
                            routeSaved = true,
                            trafficFactor = settingsRepository.currentTrafficFactor(),
                        )
                    }
                    _cameraCommand.value = CameraCommand.FitRoute(route.points, seq = cameraSeq++)
                    enrichWithElevations(route)
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
        // Seed the live camera before the first move so a cold-start nudge
        // already scales by the restored zoom (never seed cameraSaves: that
        // would rewrite DataStore with its own value).
        viewModelScope.launch {
            val saved = settingsRepository.awaitLoaded().lastCamera
            if (liveCamera.value == null) liveCamera.value = saved
        }
        // Flipping either off-road toggle reroutes the loaded stops so the line
        // (its dotted connectors and their pacing) match the setting straight away.
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.offRoadEnabled to it.offRoadWalkEnabled }
                .distinctUntilChanged()
                .drop(1)
                .collect { if (_uiState.value.waypoints.size >= 2) scheduleRouteFetch() }
        }
        // Keep the summary's traffic preview in sync with the setting toggle.
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.trafficSimEnabled }
                .distinctUntilChanged()
                .collect { _uiState.update { s -> s.copy(trafficFactor = settingsRepository.currentTrafficFactor()) } }
        }
    }

    /**
     * A placed stop means building: an idle tap opens the builder by itself.
     * [atStart] inserts a new origin instead (the held position, chosen at Play
     * time) and leaves the builder as it is.
     */
    fun addWaypoint(point: LatLng, atStart: Boolean = false) {
        if (!atStart) _builderMode.value = true
        mutate { if (atStart) listOf(Waypoint(point)) + it else it + Waypoint(point) }
    }

    /** Undo, or redo with [redo]: the builder's history, one step. */
    fun stepHistory(redo: Boolean) {
        val current = _uiState.value.waypoints
        val target = if (redo) history.redo(current) else history.undo(current)
        target?.let { mutate(refetch = !current.samePlaces(it), record = false) { _ -> it } }
    }

    fun clearWaypoints() {
        _searchedPlace.value = null
        mutate { emptyList() }
    }

    fun removeWaypoint(index: Int) {
        if (index !in _uiState.value.waypoints.indices) return
        mutate { it.filterIndexed { i, _ -> i != index } }
    }

    /**
     * Sets (or clears, with 0) the dwell at one stop. No route refetch — the
     * geometry is unchanged; the in-hand route is patched so an immediate Play
     * carries the wait.
     */
    fun setWaypointWait(index: Int, waitSeconds: Int) {
        if (index !in _uiState.value.waypoints.indices) return
        mutate(refetch = false) { stops ->
            stops.mapIndexed { i, waypoint ->
                if (i == index) waypoint.copy(waitSeconds = waitSeconds) else waypoint
            }
        }
    }

    /**
     * A marker drag: every frame relocates the stop without a fetch (the line
     * would flicker); the drop ([settled]) refetches and ends Move mode. Drag
     * frames must NOT reset interaction — Move mode is what allows the drag,
     * so resetting mid-stroke would cancel it on the first frame.
     */
    fun moveStop(index: Int, point: LatLng, settled: Boolean) {
        val before = _uiState.value.waypoints
        if (index !in before.indices) return
        if (settled) {
            // A tap-to-place (or a one-frame drop) never opened a drag: snapshot now.
            if (!history.beginDrag(before)) history.endDrag()
        } else {
            history.beginDrag(before)
        }
        if (settled) interaction.reset()
        _uiState.update { it.copy(waypoints = it.waypoints.movedTo(index, point)) }
        if (settled) scheduleRouteFetch()
    }

    /**
     * One edit of the stop list: popover/move state reset, an undo snapshot
     * (unless [record] is off — undo/redo replay one), then the route refetches
     * — or, for wait-only edits, is patched in place. An emptied list also
     * drops the in-flight elevation work.
     */
    private fun mutate(
        refetch: Boolean = true,
        record: Boolean = true,
        transform: (List<Waypoint>) -> List<Waypoint>,
    ) {
        interaction.reset()
        if (record) history.push(_uiState.value.waypoints)
        val updated = transform(_uiState.value.waypoints)
        if (updated.isEmpty()) elevationJob?.cancel()
        _uiState.update { state ->
            state.copy(
                waypoints = updated,
                route = if (refetch) state.route else state.route?.withWaits(updated),
                isRouting = state.isRouting && updated.isNotEmpty(),
            )
        }
        if (refetch) scheduleRouteFetch()
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
                name = name,
                route = route,
                profile = state.profile,
                nowEpochMillis = System.currentTimeMillis(),
            )
            _uiState.update { it.copy(savedName = name, routeSaved = true) }
        }
    }

    fun consumeSavedName() {
        _uiState.update { it.copy(savedName = null) }
    }

    /**
     * "«start» to «end», «city»" for the Save dialog, or null when either
     * lookup fails or the network is slow — the caller opens the dialog only
     * once this returns, so the name never changes under the user.
     */
    suspend fun suggestName(): String? {
        val route = _uiState.value.route ?: return null
        return withTimeoutOrNull(NAME_TIMEOUT_MILLIS) {
            val start = geocoder.reverse(route.points.first()).getOrNull()
            val end = geocoder.reverse(route.points.last()).getOrNull()
            val startName = start?.routeEndpointName() ?: return@withTimeoutOrNull null
            val endName = end?.routeEndpointName() ?: return@withTimeoutOrNull null
            val citySuffix = end.city?.let { ", $it" }.orEmpty()
            "$startName to $endName$citySuffix"
        }
    }

    /** Pans the camera (never touches mock state). */
    fun panTo(position: LatLng) {
        _cameraCommand.value = CameraCommand.Center(position, LOCATE_ZOOM, seq = cameraSeq++)
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

    /** The device's REAL position, or null; permission is the caller's job (~5 s worst case). */
    suspend fun realLocation(): LatLng? =
        locationManager.currentReal()?.let { LatLng(it.latitude, it.longitude) }

    /** One-shot cold-start camera restore, read straight from disk (no default-value race). */
    suspend fun initialCamera(): MapCamera? = settingsRepository.awaitLoaded().lastCamera

    /** Every camera frame lands here; only idle frames are persisted. */
    fun cameraChanged(camera: MapCamera, idle: Boolean) {
        liveCamera.value = camera
        if (idle) cameraSaves.value = camera
    }

    fun setFollowCamera(follow: Boolean) {
        _followCamera.value = follow
    }

    fun toggleMap3d() {
        viewModelScope.launch {
            settingsRepository.setMap3dEnabled(!settingsRepository.settings.value.map3dEnabled)
        }
    }

    /**
     * A search result: pin the exact point, centre it in the visible map at a
     * zoom for its kind, and open the builder. The band's *Add stop* (or a tap
     * on the pin) drops the stop there; a map tap elsewhere still places freely.
     */
    fun selectSearchResult(result: GeocodingResult) {
        _searchedPlace.value = result
        _cameraCommand.value = CameraCommand.Center(
            target = result.position,
            zoom = searchZoomFor(result.kind),
            seq = cameraSeq++,
            padded = true,
        )
        _builderMode.value = true
    }

    /** The searched place becomes a stop at its geocoded coordinate; the pin leaves with it. */
    fun addSearchedPlaceAsStop() {
        val place = _searchedPlace.value ?: return
        _searchedPlace.value = null
        addWaypoint(place.position)
    }

    /** Builder mode: map taps place stops; the sheet shows the route under construction. */
    fun setBuilderMode(active: Boolean) {
        interaction.reset()
        if (!active) {
            history.clear()
            _searchedPlace.value = null
        }
        _builderMode.value = active
    }

    /** Drive the route the other way round. */
    fun reverseWaypoints() {
        if (_uiState.value.waypoints.size < 2) return
        mutate { it.asReversed() }
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
        // Every stop/profile edit lands here, so this is where "saved" expires.
        _uiState.update { if (it.routeSaved) it.copy(routeSaved = false) else it }
        val state = _uiState.value
        if (state.waypoints.size < 2) {
            _uiState.update {
                it.copy(route = null, routeIsFallback = false, routedFor = null, routingError = null)
            }
            return
        }
        routeJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            _uiState.update { it.copy(isRouting = true, routingError = null) }
            val current = _uiState.value
            val positions = current.waypoints.map { it.position }
            routeProvider.route(positions, current.profile).fold(
                onSuccess = { fetched ->
                    val liveSettings = settingsRepository.settings.value
                    val stitched = if (liveSettings.offRoadEnabled) {
                        stitchOffRoad(fetched, positions, current.profile, liveSettings.offRoadWalkEnabled)
                    } else {
                        fetched
                    }
                    // Waits come from the state at completion, not the request
                    // snapshot: a wait set while the fetch was in flight (the
                    // usual case right after dropping a stop) must survive.
                    val route = _uiState.updateAndGet {
                        it.copy(
                            route = stitched.withWaits(it.waypoints),
                            routeIsFallback = false,
                            routedFor = positions,
                            isRouting = false,
                            trafficFactor = settingsRepository.currentTrafficFactor(),
                        )
                    }.route ?: return@fold
                    _cameraCommand.value = CameraCommand.EnsureVisible(route.points, seq = cameraSeq++)
                    enrichWithElevations(route)
                },
                onFailure = { error ->
                    val straight = straightLine.route(positions, current.profile).getOrNull()
                    val fallback = _uiState.updateAndGet {
                        it.copy(
                            route = straight?.withWaits(it.waypoints),
                            routeIsFallback = true,
                            routedFor = positions,
                            isRouting = false,
                            routingError = routingErrorOf(error),
                        )
                    }.route
                    fallback?.let {
                        _cameraCommand.value = CameraCommand.EnsureVisible(it.points, seq = cameraSeq++)
                    }
                },
            )
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
        const val CAMERA_SAVE_DEBOUNCE_MILLIS = 1_000L
        const val LOCATE_ZOOM = 15.0
        const val LOCATE_REFINE_METERS = 50.0
        const val NAME_TIMEOUT_MILLIS = 4_000L
    }
}

private const val LOCATE_TIMEOUT_MILLIS = 5_000L

/** The same stops with [index] relocated to [point]; its wait is kept. Out-of-range → unchanged. */
internal fun List<Waypoint>.movedTo(index: Int, point: LatLng): List<Waypoint> =
    if (index !in indices) this else mapIndexed { i, w -> if (i == index) w.copy(position = point) else w }

// Permission is gated by the UI before callers reach this.
@SuppressLint("MissingPermission")
internal fun LocationManager.quickLastKnown(): Location? =
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

private fun SettingsRepository.currentTrafficFactor(): Double =
    if (settings.value.trafficSimEnabled) {
        val now = LocalDateTime.now()
        TrafficModel.congestionFactor(now.dayOfWeek, now.hour, now.minute)
    } else {
        1.0
    }

/** Why the road route is missing; the strip words it (never the exception text). */
enum class RoutingError { NO_ROUTE, BUSY, OFFLINE, OTHER }

internal fun routingErrorOf(error: Throwable): RoutingError = when (error) {
    is RoutingException.NoRoute -> RoutingError.NO_ROUTE
    is RoutingException.RateLimited -> RoutingError.BUSY
    is RoutingException.Network -> RoutingError.OFFLINE
    else -> RoutingError.OTHER
}

/**
 * Waypoints to draw on the map: the router's road-snapped location for each
 * stop the route was actually fetched for (so markers meet the route line),
 * the raw tap otherwise. Matching is per stop by tap identity, never by list
 * size — a size gate flashed every already-snapped marker back to its raw tap
 * for the whole refetch window each time a stop was added or removed.
 */
internal fun displayWaypoints(
    waypoints: List<Waypoint>,
    route: Route?,
    routeIsFallback: Boolean,
    routedFor: List<LatLng>?,
): List<Waypoint> {
    if (route == null || routeIsFallback) return waypoints
    val snapped = route.snappedWaypoints
    if (routedFor == null || snapped.size != routedFor.size) return waypoints
    val snappedFor = routedFor.zip(snapped).toMap()
    return waypoints.map { waypoint ->
        snappedFor[waypoint.position]?.let { waypoint.copy(position = it) } ?: waypoint
    }
}

/** Attaches per-waypoint waits to a route when counts align; otherwise unchanged. */
internal fun Route.withWaits(waypoints: List<Waypoint>): Route =
    if (waypoints.size == legs.size + 1) {
        copy(waypointWaitsSeconds = waypoints.map { it.waitSeconds })
    } else {
        this
    }
