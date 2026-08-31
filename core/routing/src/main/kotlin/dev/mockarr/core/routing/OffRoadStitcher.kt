package dev.mockarr.core.routing

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.OffRoadSpan
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile

/**
 * Splices straight "off-road" connectors into a road [Route] wherever the
 * router snapped a requested stop more than [OFF_ROAD_THRESHOLD_METERS] from
 * where the user put it: the drive follows roads as far as they go, then cuts
 * across terrain to the marker — and back to the road, for via stops. The
 * connector stretches are recorded as [Route.offRoadSpans] (rendered dotted)
 * and the affected [Route.snappedWaypoints] become the requested positions,
 * since the marker is now a point the route truly reaches.
 *
 * Returns [route] unchanged when no stop is off-road or when the route's shape
 * doesn't line up with the request (defensive: stale or foreign geometry).
 * Altitudes are dropped from a stitched route — they no longer align with the
 * spliced geometry; elevation enrichment runs after stitching.
 */
fun stitchOffRoad(
    route: Route,
    requested: List<LatLng>,
    profile: RoutingProfile,
    walkingPace: Boolean = false,
): Route {
    val snapped = route.snappedWaypoints
    val boundaries = waypointPointIndices(route)
    val sizesMatch = snapped.size == requested.size && route.legs.size == requested.size - 1
    val offRoad = if (sizesMatch) {
        requested.indices.map { GeoMath.distanceMeters(requested[it], snapped[it]) > OFF_ROAD_THRESHOLD_METERS }
    } else {
        emptyList()
    }
    return if (boundaries != null && offRoad.any { it }) {
        OffRoadStitcher(route, requested, offRoad, boundaries, profile, walkingPace).build()
    } else {
        route
    }
}

/** Index of each waypoint in [Route.points], or null when the legs don't tile the geometry. */
private fun waypointPointIndices(route: Route): List<Int>? {
    val boundaries = mutableListOf(0)
    for (leg in route.legs) boundaries += boundaries.last() + leg.segmentDistancesMeters.size
    return boundaries.takeIf { route.points.isNotEmpty() && it.last() == route.points.lastIndex }
}

/** One pass over the legs, splicing connector geometry and timing in place. */
private class OffRoadStitcher(
    private val route: Route,
    private val requested: List<LatLng>,
    private val offRoad: List<Boolean>,
    private val boundaries: List<Int>,
    profile: RoutingProfile,
    walkingPace: Boolean,
) {
    private val speedMps = if (walkingPace) {
        RoutingProfile.WALKING.typicalSpeedMps
    } else {
        profile.typicalSpeedMps * OFF_ROAD_SPEED_FACTOR
    }
    private val points = mutableListOf<LatLng>()
    private val legs = mutableListOf<RouteLeg>()
    private val spans = mutableListOf<OffRoadSpan>()
    private var extraDistance = 0.0

    fun build(): Route {
        points += if (offRoad[0]) requested[0] else route.points[0]
        for (leg in route.legs.indices) legs += stitchLeg(leg)
        return route.copy(
            points = points,
            legs = legs,
            distanceMeters = route.distanceMeters + extraDistance,
            durationSeconds = route.durationSeconds + extraDistance / speedMps,
            altitudes = null,
            snappedWaypoints = requested.indices.map { i ->
                if (offRoad[i]) requested[i] else route.snappedWaypoints[i]
            },
            offRoadSpans = spans,
        )
    }

    private fun stitchLeg(leg: Int): RouteLeg {
        val distances = mutableListOf<Double>()
        val durations = mutableListOf<Double>()
        // Depart an off-road stop: straight back to where the road resumes.
        if (offRoad[leg]) {
            appendConnector(route.points[boundaries[leg]], distances, durations)
        }
        for (i in boundaries[leg] + 1..boundaries[leg + 1]) points += route.points[i]
        distances += route.legs[leg].segmentDistancesMeters
        durations += route.legs[leg].segmentDurationsSeconds
        // Arrive at an off-road stop: leave the road at its nearest point.
        if (offRoad[leg + 1]) {
            appendConnector(requested[leg + 1], distances, durations)
        }
        return RouteLeg(distances, durations)
    }

    /** Straight geometry from the current last point to [target], tracked as an off-road span. */
    private fun appendConnector(
        target: LatLng,
        distances: MutableList<Double>,
        durations: MutableList<Double>,
    ) {
        val (connectorPoints, connectorDistances) = straightSegments(points.last(), target)
        spans += OffRoadSpan(points.lastIndex, points.lastIndex + connectorPoints.size)
        points += connectorPoints
        distances += connectorDistances
        durations += connectorDistances.map { it / speedMps }
        extraDistance += connectorDistances.sum()
    }
}

/** Snap displacement beyond this is "the road doesn't go there" rather than kerb-to-centreline noise. */
const val OFF_ROAD_THRESHOLD_METERS = 25.0

/** Terrain is slower than the profile's typical road speed. */
private const val OFF_ROAD_SPEED_FACTOR = 0.5
