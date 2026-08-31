package dev.mockarr.core.routing

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile

/**
 * Great-circle fallback used when road routing fails: straight legs between
 * waypoints, interpolated so playback still has dense geometry.
 */
class StraightLineRouteProvider : RouteProvider {

    override suspend fun route(waypoints: List<LatLng>, profile: RoutingProfile): Result<Route> {
        require(waypoints.size >= 2) { "At least two waypoints are required" }
        val speed = profile.typicalSpeedMps
        val points = mutableListOf(waypoints.first())
        val legs = mutableListOf<RouteLeg>()
        var totalDistance = 0.0
        for (i in 0 until waypoints.size - 1) {
            val (legPoints, segmentDistances) = straightSegments(waypoints[i], waypoints[i + 1])
            points += legPoints
            legs += RouteLeg(
                segmentDistancesMeters = segmentDistances,
                segmentDurationsSeconds = segmentDistances.map { it / speed },
            )
            totalDistance += segmentDistances.sum()
        }
        return Result.success(
            Route(
                points = points,
                legs = legs,
                distanceMeters = totalDistance,
                durationSeconds = totalDistance / speed,
            ),
        )
    }
}

/**
 * The points after [from] (up to and including [to]) plus per-segment
 * distances, stepped about [STRAIGHT_STEP_METERS] apart so playback has dense
 * geometry. Shared by the fallback provider and the off-road stitcher.
 */
internal fun straightSegments(from: LatLng, to: LatLng): Pair<List<LatLng>, List<Double>> {
    val total = GeoMath.distanceMeters(from, to)
    val steps = (total / STRAIGHT_STEP_METERS).toInt().coerceAtLeast(1)
    val bearing = GeoMath.bearingDegrees(from, to)
    val points = mutableListOf<LatLng>()
    val distances = mutableListOf<Double>()
    var previous = from
    for (step in 1..steps) {
        val next = if (step == steps) {
            to
        } else {
            GeoMath.destination(from, bearing, total * step / steps)
        }
        points += next
        distances += GeoMath.distanceMeters(previous, next)
        previous = next
    }
    return points to distances
}

internal val RoutingProfile.typicalSpeedMps: Double
    get() = when (this) {
        RoutingProfile.DRIVING -> 13.9
        RoutingProfile.WALKING -> 1.4
        RoutingProfile.CYCLING -> 4.2
    }

private const val STRAIGHT_STEP_METERS = 50.0
