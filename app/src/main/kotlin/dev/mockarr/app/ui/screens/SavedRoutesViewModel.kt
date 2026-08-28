package dev.mockarr.app.ui.screens

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.ui.RouteHandoff
import dev.mockarr.app.ui.map.RouteThumbnails
import dev.mockarr.app.ui.map.ThumbSpec
import dev.mockarr.app.ui.theme.MapPalette
import dev.mockarr.core.data.SavedRouteEntity
import dev.mockarr.core.data.SavedRoutesRepository
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.RoutingProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedRoutesViewModel @Inject constructor(
    private val repository: SavedRoutesRepository,
    private val routeHandoff: RouteHandoff,
    private val thumbnails: RouteThumbnails,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    init {
        // Drop cached thumbnails for routes deleted since the last visit.
        viewModelScope.launch {
            thumbnails.pruneExcept(repository.observeAll().first().map { it.id }.toSet())
        }
    }

    enum class Sort { RECENT, LONGEST, NAME }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(Sort.RECENT)
    val sort: StateFlow<Sort> = _sort.asStateFlow()

    /** null = every travel mode. */
    private val _profileFilter = MutableStateFlow<RoutingProfile?>(null)
    val profileFilter: StateFlow<RoutingProfile?> = _profileFilter.asStateFlow()

    /** True once any route exists at all — separates "empty" from "nothing matches". */
    val hasAnyRoutes: StateFlow<Boolean> = repository.observeAll()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    val routes: StateFlow<List<SavedRouteEntity>> =
        combine(repository.observeAll(), _query, _sort, _profileFilter) { all, query, sort, profile ->
            filterAndSort(all, query, sort, profile)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setSort(value: Sort) {
        _sort.value = value
    }

    fun setProfileFilter(value: RoutingProfile?) {
        _profileFilter.value = value
    }

    fun rename(entity: SavedRouteEntity, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.rename(entity, name) }
    }

    val units: StateFlow<DistanceUnits> = settingsRepository.settings
        .map { it.units }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsRepository.settings.value.units,
        )

    val mapStyleUrl: StateFlow<String> = settingsRepository.settings
        .map { it.tileStyleUrl }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsRepository.settings.value.tileStyleUrl,
        )

    /** Real-basemap thumbnail, or null (loading/offline — caller shows the glyph). */
    suspend fun thumbnail(
        entity: SavedRouteEntity,
        styleUrl: String,
        sizePx: Int,
        density: Float,
        palette: MapPalette,
    ): ImageBitmap? = thumbnails.bitmapFor(
        ThumbSpec(
            routeId = entity.id,
            createdAtEpochMillis = entity.createdAtEpochMillis,
            encodedPolyline6 = entity.encodedPolyline6,
            styleUrl = styleUrl,
            sizePx = sizePx,
            density = density,
            palette = palette,
        ),
    )?.asImageBitmap()

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

internal fun filterAndSort(
    all: List<SavedRouteEntity>,
    query: String,
    sort: SavedRoutesViewModel.Sort,
    profile: RoutingProfile?,
): List<SavedRouteEntity> {
    val trimmed = query.trim()
    val filtered = all.filter { entity ->
        (trimmed.isEmpty() || entity.name.contains(trimmed, ignoreCase = true)) &&
            (profile == null || RoutingProfile.fromNameOrDefault(entity.profile) == profile)
    }
    return when (sort) {
        SavedRoutesViewModel.Sort.RECENT -> filtered.sortedByDescending { it.createdAtEpochMillis }
        SavedRoutesViewModel.Sort.LONGEST -> filtered.sortedByDescending { it.distanceMeters }
        SavedRoutesViewModel.Sort.NAME -> filtered.sortedBy { it.name.lowercase() }
    }
}
