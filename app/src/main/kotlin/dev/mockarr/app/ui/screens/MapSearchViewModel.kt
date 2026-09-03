package dev.mockarr.app.ui.screens

import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.playback.MockSessionRepository
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.core.data.RecentSearchesStore
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.MapCamera
import dev.mockarr.core.routing.Geocoder
import dev.mockarr.core.routing.GeocodingResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Place search for the Map tab, tuned to feel like a consumer nav app
 * (`docs/research/search-rnd.md`): typeahead from two characters, recents at
 * 0 ms, cached prefixes while the network answers, results ranked around the
 * viewport. Split from MapViewModel so each stays under detekt's function cap.
 */
@HiltViewModel
class MapSearchViewModel @Inject constructor(
    private val geocoder: Geocoder,
    private val locationManager: LocationManager,
    private val sessionRepository: MockSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val recentSearches: RecentSearchesStore,
) : ViewModel() {

    enum class Status { IDLE, NO_MATCH, FAILED }

    data class SearchState(
        val query: String = "",
        val searching: Boolean = false,
        val results: List<SearchSuggestion> = emptyList(),
        val status: Status = Status.IDLE,
        /** The text field has focus — a map tap should close the search, not place a stop. */
        val fieldFocused: Boolean = false,
    )

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private val cache = PrefixCache()

    /** A pick or Close hides the list until the field is focused or typed in again. */
    private var listHidden = false

    /** Recents belong to a focused, empty field only — never to a cold start. */
    private var fieldFocused = false

    /** Camera the map last idled at; the ranking anchor unless a mock is live. */
    var cameraBias: MapCamera? = null

    init {
        // Seed the bias so pre-pan queries still rank nearby places first.
        viewModelScope.launch {
            val saved = settingsRepository.awaitLoaded().lastCamera
            if (cameraBias == null) cameraBias = saved
        }
        // Recents change (a pick, a clear): re-render whatever the field holds.
        viewModelScope.launch {
            recentSearches.recents.collect {
                val query = _state.value.query
                if (!listHidden && (fieldFocused || query.isNotBlank())) {
                    showLocal(query, searching = _state.value.searching)
                }
            }
        }
    }

    /** As-you-type: local answers now, the network after a short debounce. */
    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        listHidden = false
        val trimmed = query.trim()
        val enough = trimmed.length >= MIN_QUERY_LENGTH
        showLocal(trimmed, searching = enough)
        if (!enough) return
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

    /**
     * The field lost its purpose (a pick, Close): the list and the text go —
     * the placed stop or pin marks the place now, and a field that keeps old
     * text re-opened its list on the next resume (critique, 2026-08-30).
     */
    fun clearResults() {
        searchJob?.cancel()
        listHidden = true
        _state.update { SearchState(fieldFocused = fieldFocused) }
    }

    /** Focus gained: recents for an empty field; a field with text keeps whatever it shows. */
    fun setFieldFocused(focused: Boolean) {
        fieldFocused = focused
        _state.update { it.copy(fieldFocused = focused) }
        if (!focused || _state.value.query.isNotBlank()) return
        listHidden = false
        showLocal("", searching = false)
    }

    fun rememberPick(result: GeocodingResult) {
        viewModelScope.launch { recentSearches.remember(result, System.currentTimeMillis()) }
    }

    fun clearRecents() {
        viewModelScope.launch { recentSearches.clear() }
    }

    /** Recents matching [query] plus cached prefix hits — renders within the frame. */
    private fun showLocal(query: String, searching: Boolean) {
        val trimmed = query.trim()
        val cached = if (trimmed.length >= MIN_QUERY_LENGTH) cache.lookup(trimmed).orEmpty() else emptyList()
        val merged = mergeSuggestions(matchingRecents(recentSearches.recents.value, trimmed), cached, anchor())
        _state.update { it.copy(searching = searching, results = merged, status = Status.IDLE) }
    }

    private suspend fun runSearch(query: String) {
        _state.update { it.copy(searching = true) }
        val anchor = anchor()
        geocoder.search(query, bias = anchor, zoom = biasZoom(cameraBias?.zoom)).fold(
            onSuccess = { results ->
                cache.put(query, results)
                // Stale guard: the field may have moved on while this was in flight.
                if (!_state.value.query.trim().equals(query, ignoreCase = true)) return
                val merged = mergeSuggestions(matchingRecents(recentSearches.recents.value, query), results, anchor)
                val status = if (merged.isEmpty()) Status.NO_MATCH else Status.IDLE
                _state.update { it.copy(searching = false, results = merged, status = status) }
            },
            onFailure = {
                if (!_state.value.query.trim().equals(query, ignoreCase = true)) return
                _state.update { it.copy(searching = false, status = Status.FAILED) }
            },
        )
    }

    private fun anchor(): LatLng? = searchAnchor(
        mocked = mockedPosition(),
        camera = cameraBias?.target,
        real = locationManager.quickLastKnown()?.let { LatLng(it.latitude, it.longitude) },
    )

    private fun mockedPosition(): LatLng? = when (val session = sessionRepository.session.value) {
        is MockSessionState.Holding -> session.position
        is MockSessionState.Playing -> sessionRepository.latestFix.value?.position
        else -> null
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MILLIS = 200L
    }
}
