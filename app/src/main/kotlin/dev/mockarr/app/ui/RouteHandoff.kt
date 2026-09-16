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

    /** [saved] is false for a route that only lives in memory (an interrupted drive), so Save stays offered. */
    data class LoadedRoute(val route: Route, val profile: RoutingProfile, val saved: Boolean = true)

    private val _pending = MutableStateFlow<LoadedRoute?>(null)
    val pending: StateFlow<LoadedRoute?> = _pending.asStateFlow()

    fun set(route: Route, profile: RoutingProfile, saved: Boolean = true) {
        _pending.value = LoadedRoute(route, profile, saved)
    }

    fun clear() {
        _pending.value = null
    }
}
