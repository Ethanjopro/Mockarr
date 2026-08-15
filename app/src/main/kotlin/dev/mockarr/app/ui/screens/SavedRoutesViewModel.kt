package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.ui.RouteHandoff
import dev.mockarr.core.data.SavedRouteEntity
import dev.mockarr.core.data.SavedRoutesRepository
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.model.DistanceUnits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedRoutesViewModel @Inject constructor(
    private val repository: SavedRoutesRepository,
    private val routeHandoff: RouteHandoff,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val routes: StateFlow<List<SavedRouteEntity>> =
        combine(repository.observeAll(), _query) { all, query ->
            val trimmed = query.trim()
            if (trimmed.isEmpty()) all else all.filter { it.name.contains(trimmed, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    val units: StateFlow<DistanceUnits> = settingsRepository.settings
        .map { it.units }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsRepository.settings.value.units,
        )

    /** Puts the route on the map; caller navigates to the Map tab. */
    fun load(entity: SavedRouteEntity) {
        routeHandoff.set(repository.toRoute(entity), repository.profileOf(entity))
    }

    fun delete(entity: SavedRouteEntity) {
        viewModelScope.launch { repository.delete(entity) }
    }

    fun restore(entity: SavedRouteEntity) {
        viewModelScope.launch { repository.restore(entity) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
