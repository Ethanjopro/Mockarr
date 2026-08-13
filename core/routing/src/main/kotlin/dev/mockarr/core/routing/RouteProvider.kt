package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile

/**
 * Produces a [Route] through the given waypoints. Implementations wrap a routing
 * backend (OSRM by default); swapping backends must not affect callers.
 */
interface RouteProvider {
    suspend fun route(waypoints: List<LatLng>, profile: RoutingProfile): Result<Route>
}
