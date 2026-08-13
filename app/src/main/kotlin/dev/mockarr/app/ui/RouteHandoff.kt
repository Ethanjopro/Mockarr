package dev.mockarr.app.ui

import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Hands a saved route from the Routes screen to the Map screen's ViewModel. */
@Singleton
class RouteHandoff @Inject constructor() {

    data class LoadedRoute(val route: Route, val profile: RoutingProfile)

    private val _pending = MutableStateFlow<LoadedRoute?>(null)
    val pending: StateFlow<LoadedRoute?> = _pending.asStateFlow()

    fun set(route: Route, profile: RoutingProfile) {
        _pending.value = LoadedRoute(route, profile)
    }

    fun clear() {
        _pending.value = null
    }
}
