package dev.mockarr.app.ui.screens

import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.playback.MockSessionRepository
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.routing.GeocodingResult
import dev.mockarr.core.routing.PhotonGeocoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Place search for the Map tab: debounced as-you-type lookups ranked around
 * the mocked position (then the real one, then the camera). Split from
 * MapViewModel so each stays under detekt's function cap and search can be
 * reused by the builder without dragging route state along.
 */
@HiltViewModel
class MapSearchViewModel @Inject constructor(
    private val geocoder: PhotonGeocoder,
    private val locationManager: LocationManager,
    private val sessionRepository: MockSessionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

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

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    /** Camera target the map last idled at; the weakest ranking anchor. */
    var cameraBias: LatLng? = null

    init {
        // Seed the bias so pre-pan queries still rank nearby places first.
        viewModelScope.launch {
            val saved = settingsRepository.awaitLoaded().lastCamera
            if (cameraBias == null) cameraBias = saved?.target
        }
    }

    /** As-you-type: debounce, cancel the in-flight lookup, bias results toward the camera. */
    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(searching = false, results = emptyList(), errorMessage = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            runSearch(trimmed)
        }
    }

    /** IME search action — skip the debounce. */
    fun submit() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    fun clearResults() {
        _state.update { it.copy(results = emptyList(), errorMessage = null) }
    }

    private suspend fun runSearch(query: String) {
        _state.update { it.copy(searching = true, errorMessage = null) }
        val bias = mockedPosition()
            ?: locationManager.quickLastKnown()?.let { LatLng(it.latitude, it.longitude) }
            ?: cameraBias
        geocoder.search(query, bias = bias).fold(
            onSuccess = { results ->
                val suggestions = results.map { result ->
                    SearchSuggestion(
                        result = result,
                        distanceMeters = bias?.let { GeoMath.distanceMeters(it, result.position) },
                    )
                }
                _state.update {
                    it.copy(
                        searching = false,
                        results = suggestions,
                        errorMessage = if (suggestions.isEmpty()) "No places found" else null,
                    )
                }
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        searching = false,
                        errorMessage = "Search failed: ${error.message ?: "network error"}",
                    )
                }
            },
        )
    }

    private fun mockedPosition(): LatLng? = when (val session = sessionRepository.session.value) {
        is MockSessionState.Holding -> session.position
        is MockSessionState.Playing -> sessionRepository.latestFix.value?.position
        else -> null
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 3
        const val SEARCH_DEBOUNCE_MILLIS = 350L
    }
}
