package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Precomputed playback geometry for a [Route]: cumulative distances, per-segment
 * target speeds (from OSRM annotations when available), and per-vertex allowed
 * entry speeds that encode turn slow-downs and a kinematic braking curve
 * (`v² ≤ v_target² + 2·decel·d`) so approaches to turns and the final stop look
 * human instead of instantaneous.
 */
class RouteGeometry(
    route: Route,
    decelerationMps2: Double,
    durationScale: Double = 1.0,
) {
    private val points: List<LatLng> = route.points

    /** Terrain profile aligned with [points]; dropped if misaligned. */
    private val altitudes: List<Double>? = route.altitudes?.takeIf { it.size == route.points.size }
    val totalDistanceMeters: Double
    val totalDurationSeconds: Double

    /** cumulative[i] = distance from start to points[i]; size = points.size */
    private val cumulative: DoubleArray

    /** cumulativeDurations[i] = seconds from start to points[i] at segment cruise speeds. */
    private val cumulativeDurations: DoubleArray

    /** Target cruise speed for segment i (points[i]..points[i+1]). */
    internal val segmentSpeeds: DoubleArray

    /** Bearing of segment i in degrees. */
    private val segmentBearings: DoubleArray

    /** Max speed allowed *at* vertex i (turn caps + braking backward pass). */
    internal val allowedVertexSpeeds: DoubleArray

    init {
        require(points.size >= 2) { "A route needs at least two points" }
        val n = points.size

        cumulative = DoubleArray(n)
        segmentBearings = DoubleArray(n - 1)
        for (i in 0 until n - 1) {
            cumulative[i + 1] = cumulative[i] + GeoMath.distanceMeters(points[i], points[i + 1])
            segmentBearings[i] = GeoMath.bearingDegrees(points[i], points[i + 1])
        }
        totalDistanceMeters = cumulative[n - 1]

        segmentSpeeds =
            computeSegmentSpeeds(route, n, durationScale.coerceAtLeast(MIN_DURATION_SCALE))

        cumulativeDurations = DoubleArray(n)
        for (i in 0 until n - 1) {
            cumulativeDurations[i + 1] =
                cumulativeDurations[i] + (cumulative[i + 1] - cumulative[i]) / segmentSpeeds[i]
        }
        totalDurationSeconds = cumulativeDurations[n - 1]

        // Turn caps at interior vertices, then a backward pass so every vertex
        // speed is reachable under the deceleration limit.
        allowedVertexSpeeds = DoubleArray(n)
        allowedVertexSpeeds[n - 1] = 0.0 // always come to rest at the destination
        for (i in n - 2 downTo 1) {
            val brakingCap = brakingLimit(
                allowedVertexSpeeds[i + 1],
                decelerationMps2,
                cumulative[i + 1] - cumulative[i],
            )
            allowedVertexSpeeds[i] = min(turnCapAt(i), brakingCap)
        }
        allowedVertexSpeeds[0] = 0.0 // start from rest
    }

    fun segmentIndexAt(distance: Double): Int {
        val clamped = distance.coerceIn(0.0, totalDistanceMeters)
        var low = 0
        var high = cumulative.size - 1
        while (low < high - 1) {
            val mid = (low + high) / 2
            if (cumulative[mid] <= clamped) low = mid else high = mid
        }
        return low.coerceAtMost(segmentSpeeds.size - 1)
    }

    fun positionAt(distance: Double): LatLng {
        val clamped = distance.coerceIn(0.0, totalDistanceMeters)
        val i = segmentIndexAt(clamped)
        val segStart = cumulative[i]
        val segLength = cumulative[i + 1] - segStart
        if (segLength <= 0.0) return points[i]
        val t = ((clamped - segStart) / segLength).coerceIn(0.0, 1.0)
        val a = points[i]
        val b = points[i + 1]
        return LatLng(
            a.latitude + (b.latitude - a.latitude) * t,
            a.longitude + (b.longitude - a.longitude) * t,
        )
    }

    fun bearingAt(distance: Double): Double = segmentBearings[segmentIndexAt(distance)]

    /** Estimated seconds from [distance] to the destination at segment cruise speeds. */
    fun remainingDurationSeconds(distance: Double): Double {
        val clamped = distance.coerceIn(0.0, totalDistanceMeters)
        val i = segmentIndexAt(clamped)
        val elapsed = cumulativeDurations[i] + (clamped - cumulative[i]) / segmentSpeeds[i]
        return (totalDurationSeconds - elapsed).coerceAtLeast(0.0)
    }

    /** Terrain altitude at [distance] (segment lerp), or null without an elevation profile. */
    fun altitudeAt(distance: Double): Double? {
        val profile = altitudes ?: return null
        val clamped = distance.coerceIn(0.0, totalDistanceMeters)
        val i = segmentIndexAt(clamped)
        val segStart = cumulative[i]
        val segLength = cumulative[i + 1] - segStart
        if (segLength <= 0.0) return profile[i]
        val t = ((clamped - segStart) / segLength).coerceIn(0.0, 1.0)
        return profile[i] + (profile[i + 1] - profile[i]) * t
    }

    fun distanceToVertex(distance: Double, vertexIndex: Int): Double =
        (cumulative[vertexIndex] - distance).coerceAtLeast(0.0)

    /** [durationScale] > 1 slows cruise speeds (simulated congestion), applied before the clamp. */
    private fun computeSegmentSpeeds(route: Route, n: Int, durationScale: Double): DoubleArray {
        val flattenedDistances = route.legs.flatMap { it.segmentDistancesMeters }
        val flattenedDurations = route.legs.flatMap { it.segmentDurationsSeconds }
        val uniformSpeed = if (route.durationSeconds > 0) {
            (route.distanceMeters / (route.durationSeconds * durationScale))
                .coerceIn(MIN_SPEED, MAX_SPEED)
        } else {
            DEFAULT_SPEED
        }
        val speeds = DoubleArray(n - 1) { uniformSpeed }
        if (flattenedDistances.size == n - 1 && flattenedDurations.size == n - 1) {
            var previous = uniformSpeed
            for (i in speeds.indices) {
                val d = flattenedDistances[i]
                val t = flattenedDurations[i]
                previous = if (t > 0 && d > 0) {
                    (d / (t * durationScale)).coerceIn(MIN_SPEED, MAX_SPEED)
                } else {
                    previous
                }
                speeds[i] = previous
            }
        }
        return speeds
    }

    private fun turnCapAt(vertex: Int): Double {
        val incoming = segmentBearings[vertex - 1]
        val outgoing = segmentBearings[vertex]
        val turn = abs(shortestAngleDelta(incoming, outgoing))
        val cruise = min(segmentSpeeds[vertex - 1], segmentSpeeds[vertex])
        return max(MIN_TURN_SPEED, cruise * (1 - turn / HALF_TURN_DEGREES * TURN_SEVERITY))
    }

    companion object {
        internal const val MIN_DURATION_SCALE = 0.1
        internal const val MIN_SPEED = 0.5
        internal const val MAX_SPEED = 42.0
        internal const val DEFAULT_SPEED = 10.0
        internal const val MIN_TURN_SPEED = 2.0
        private const val HALF_TURN_DEGREES = 180.0
        private const val TURN_SEVERITY = 0.9

        /** Fastest speed from which [decelerationMps2] can reach [allowedAhead] within [distance]. */
        internal fun brakingLimit(allowedAhead: Double, decelerationMps2: Double, distance: Double): Double =
            sqrt(allowedAhead * allowedAhead + 2 * decelerationMps2 * distance)

        /** Signed smallest angle from [from] to [to], in (-180, 180]. */
        internal fun shortestAngleDelta(from: Double, to: Double): Double {
            var delta = (to - from) % 360.0
            if (delta > 180.0) delta -= 360.0
            if (delta <= -180.0) delta += 360.0
            return delta
        }
    }
}
