package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.ui.RouteHandoff
import dev.mockarr.core.data.SavedRouteEntity
import dev.mockarr.core.data.SavedRoutesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedRoutesViewModel @Inject constructor(
    private val repository: SavedRoutesRepository,
    private val routeHandoff: RouteHandoff,
) : ViewModel() {

    val routes: StateFlow<List<SavedRouteEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

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
