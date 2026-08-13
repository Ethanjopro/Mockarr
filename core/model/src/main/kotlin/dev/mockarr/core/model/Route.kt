package dev.mockarr.core.model

/**
 * A road route between waypoints, independent of the routing backend that produced it.
 *
 * @property points the full route geometry, ordered from start to destination
 * @property legs one leg per waypoint pair, carrying per-segment timing used to
 *   derive realistic playback speeds
 */
data class Route(
    val points: List<LatLng>,
    val legs: List<RouteLeg>,
    val distanceMeters: Double,
    val durationSeconds: Double,
)

/**
 * Per-segment measurements for one leg of a [Route]. A segment is the stretch
 * between two adjacent geometry points; index `i` covers `points[i]..points[i + 1]`.
 */
data class RouteLeg(
    val segmentDistancesMeters: List<Double>,
    val segmentDurationsSeconds: List<Double>,
)
