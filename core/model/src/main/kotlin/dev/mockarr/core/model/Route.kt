package dev.mockarr.core.model

import kotlinx.serialization.Serializable

/**
 * A road route between waypoints, independent of the routing backend that produced it.
 *
 * @property points the full route geometry, ordered from start to destination
 * @property legs one leg per waypoint pair, carrying per-segment timing used to
 *   derive realistic playback speeds
 * @property altitudes terrain elevation in meters, aligned 1:1 with [points];
 *   null until (or unless) elevation enrichment has run
 * @property snappedWaypoints the requested waypoints snapped onto the road
 *   network by the router, aligned 1:1 with the request; empty when the
 *   backend provides none (e.g. straight-line fallback)
 * @property offRoadSpans stretches of [points] that leave the road network
 *   (straight connectors to a stop the router couldn't reach); rendered dotted
 */
@Serializable
data class Route(
    val points: List<LatLng>,
    val legs: List<RouteLeg>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val altitudes: List<Double>? = null,
    val snappedWaypoints: List<LatLng> = emptyList(),
    /**
     * Dwell seconds per requested waypoint, aligned 1:1 with the waypoints
     * (`legs.size + 1`) when non-empty; empty = no waits.
     */
    val waypointWaitsSeconds: List<Int> = emptyList(),
    val offRoadSpans: List<OffRoadSpan> = emptyList(),
    /** Each requested waypoint's name ([Waypoint.name]), aligned like [waypointWaitsSeconds]; empty = unnamed. */
    val waypointNames: List<String?> = emptyList(),
)

/**
 * This route with [leadIn] driven first: the drive in from a held spot or the real
 * location to this route's start. The lead-in's origin becomes waypoint 0, so every
 * waypoint list (snaps, waits, names) gains one entry in front; the planned route
 * itself is untouched. Where the lead-in doesn't end exactly on this route's first
 * point, a straight connector segment bridges the gap at the lead-in's pace.
 */
fun Route.withLeadIn(leadIn: Route): Route {
    val gap = GeoMath.distanceMeters(leadIn.points.last(), points.first())
    val seamless = gap < LEAD_IN_SEAM_METERS
    val pace = if (leadIn.durationSeconds > 0.0) leadIn.distanceMeters / leadIn.durationSeconds else 0.0
    val connectorSeconds = if (seamless || pace <= 0.0) 0.0 else gap / pace
    // This route's point k sits at [shift] + k in the joined list.
    val shift = leadIn.points.size - if (seamless) 1 else 0
    return Route(
        points = leadIn.points + if (seamless) points.drop(1) else points,
        legs = leadIn.legsCovering().withConnector(if (seamless) null else gap, connectorSeconds) + legs,
        distanceMeters = leadIn.distanceMeters + gap + distanceMeters,
        durationSeconds = leadIn.durationSeconds + connectorSeconds + durationSeconds,
        altitudes = joinedAltitudes(leadIn, seamless),
        snappedWaypoints = if (snappedWaypoints.isEmpty()) {
            emptyList()
        } else {
            listOf(leadIn.snappedWaypoints.firstOrNull() ?: leadIn.points.first()) + snappedWaypoints
        },
        waypointWaitsSeconds = if (waypointWaitsSeconds.isEmpty()) emptyList() else listOf(0) + waypointWaitsSeconds,
        offRoadSpans = leadIn.offRoadSpans + offRoadSpans.map { OffRoadSpan(it.start + shift, it.end + shift) },
        waypointNames = if (waypointNames.isEmpty()) emptyList() else listOf(null) + waypointNames,
    )
}

/** [gap] metres more on the last leg (a straight connector), or the legs as they are for null. */
private fun List<RouteLeg>.withConnector(gap: Double?, seconds: Double): List<RouteLeg> {
    if (gap == null) return this
    val last = last()
    return dropLast(1) + last.copy(
        segmentDistancesMeters = last.segmentDistancesMeters + gap,
        segmentDurationsSeconds = last.segmentDurationsSeconds + seconds,
    )
}

/**
 * The joined terrain profile: the lead-in's own, or — with none — level with this
 * route's start rather than dropping the whole profile. Null when this route has none.
 */
private fun Route.joinedAltitudes(leadIn: Route, seamless: Boolean): List<Double>? {
    val own = altitudes ?: return null
    val lead = leadIn.altitudes?.takeIf { it.size == leadIn.points.size }
        ?: own.firstOrNull()?.let { start -> List(leadIn.points.size) { start } }
        ?: return null
    return lead + if (seamless) own.drop(1) else own
}

/**
 * One leg whose segments cover every point pair — what [withLeadIn] needs to keep the
 * joined route's segments aligned with its points (a backend may send none).
 */
private fun Route.legsCovering(): List<RouteLeg> {
    if (legs.isNotEmpty() && legs.sumOf { it.segmentDistancesMeters.size } == points.size - 1) return legs
    val distances = points.zipWithNext { a, b -> GeoMath.distanceMeters(a, b) }
    val total = distances.sum()
    val durations = distances.map { if (total > 0.0) durationSeconds * it / total else 0.0 }
    return listOf(RouteLeg(distances, durations))
}

/** Closer than this, a lead-in's end and the route's start are one point. */
private const val LEAD_IN_SEAM_METERS = 1.0

/** An off-road stretch of a [Route]: point indices [start]..[end], inclusive. */
@Serializable
data class OffRoadSpan(val start: Int, val end: Int)

/**
 * Per-segment measurements for one leg of a [Route]. A segment is the stretch
 * between two adjacent geometry points; index `i` covers `points[i]..points[i + 1]`.
 */
@Serializable
data class RouteLeg(
    val segmentDistancesMeters: List<Double>,
    val segmentDurationsSeconds: List<Double>,
)
