package dev.mockarr.app.playback

import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.withLeadIn
import dev.mockarr.core.routing.ElevationProvider
import dev.mockarr.core.routing.ElevationSampling
import dev.mockarr.core.routing.RouteProvider
import dev.mockarr.core.routing.StraightLineRouteProvider
import dev.mockarr.core.routing.stitchOffRoad
import javax.inject.Inject

/**
 * The drive in from somewhere else (a held spot, the real location) to a route's
 * start — routed the way the builder routes (off-road stitching, a straight line when
 * the router can't), given terrain, and driven in front of the route. The route itself
 * never changes: a saved route stays saved and "Drive again" stays repeatable
 * (Ethan, 2026-09-24; it used to gain the origin as a permanent first stop).
 */
class DriveInPlanner @Inject constructor(
    private val routeProvider: RouteProvider,
    private val elevationClient: ElevationProvider,
    private val settingsRepository: SettingsRepository,
) {
    private val straightLine = StraightLineRouteProvider()

    /** [route] led into from [origin]; [route] unchanged when not even a straight line can be drawn. */
    suspend fun leadInto(route: Route, origin: LatLng, profile: RoutingProfile): Route {
        val positions = listOf(origin, route.points.first())
        val settings = settingsRepository.settings.value
        val leadIn = routeProvider.route(positions, profile)
            .map { fetched ->
                if (settings.offRoadEnabled) {
                    stitchOffRoad(fetched, positions, profile, settings.offRoadWalkEnabled)
                } else {
                    fetched
                }
            }
            .getOrElse { straightLine.route(positions, profile).getOrNull() }
            ?: return route
        return route.withLeadIn(withTerrain(leadIn))
    }

    // Offline or unavailable: the lead-in rides level with the route's start (withLeadIn).
    private suspend fun withTerrain(leg: Route): Route {
        if (leg.altitudes != null) return leg
        val indices = ElevationSampling.sampleIndices(leg.points)
        val sampled = elevationClient.elevations(indices.map { leg.points[it] }).getOrNull() ?: return leg
        return leg.copy(altitudes = ElevationSampling.interpolate(leg.points, indices, sampled))
    }
}
