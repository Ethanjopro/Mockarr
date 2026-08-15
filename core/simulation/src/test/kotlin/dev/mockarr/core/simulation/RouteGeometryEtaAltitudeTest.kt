package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteGeometryEtaAltitudeTest {

    /** 1 000 m straight east at a uniform 10 m/s (100 s total). */
    private fun straightRoute(altitudes: List<Double>? = null): Route {
        val start = LatLng(0.0, 0.0)
        val points = (0..10).map { GeoMath.destination(start, 90.0, 100.0 * it) }
        val distances = List(10) { 100.0 }
        return Route(
            points = points,
            legs = listOf(RouteLeg(distances, distances.map { it / 10.0 })),
            distanceMeters = 1000.0,
            durationSeconds = 100.0,
            altitudes = altitudes,
        )
    }

    @Test
    fun `remaining duration interpolates from full to zero`() {
        val geometry = RouteGeometry(straightRoute(), decelerationMps2 = 3.0)
        assertEquals(100.0, geometry.remainingDurationSeconds(0.0), 1.0)
        assertEquals(50.0, geometry.remainingDurationSeconds(500.0), 1.0)
        assertEquals(0.0, geometry.remainingDurationSeconds(geometry.totalDistanceMeters), 0.01)
    }

    @Test
    fun `altitude lerps between vertices`() {
        val altitudes = (0..10).map { it * 10.0 } // 0 m at start, 100 m at end
        val geometry = RouteGeometry(straightRoute(altitudes), decelerationMps2 = 3.0)
        assertEquals(0.0, geometry.altitudeAt(0.0)!!, 0.5)
        assertEquals(50.0, geometry.altitudeAt(500.0)!!, 0.6)
        assertEquals(100.0, geometry.altitudeAt(geometry.totalDistanceMeters)!!, 0.5)
        assertEquals(5.0, geometry.altitudeAt(50.0)!!, 0.6) // mid-segment
    }

    @Test
    fun `altitude is null without a profile`() {
        val geometry = RouteGeometry(straightRoute(), decelerationMps2 = 3.0)
        assertNull(geometry.altitudeAt(100.0))
    }

    @Test
    fun `misaligned altitude profile is dropped`() {
        val geometry = RouteGeometry(straightRoute(listOf(1.0, 2.0)), decelerationMps2 = 3.0)
        assertNull(geometry.altitudeAt(100.0))
    }

    @Test
    fun `duration scale stretches durations and slows speeds`() {
        val base = RouteGeometry(straightRoute(), decelerationMps2 = 3.0)
        val congested = RouteGeometry(straightRoute(), decelerationMps2 = 3.0, durationScale = 1.5)
        assertEquals(base.totalDurationSeconds * 1.5, congested.totalDurationSeconds, 1.0)
        assertEquals(
            base.segmentSpeeds[0] / 1.5,
            congested.segmentSpeeds[0],
            0.01,
        )
        assertEquals(
            base.remainingDurationSeconds(500.0) * 1.5,
            congested.remainingDurationSeconds(500.0),
            1.0,
        )
    }

    @Test
    fun `speed variance spreads segments within bounds and keeps durations consistent`() {
        val base = RouteGeometry(straightRoute(), decelerationMps2 = 3.0)
        val varied = RouteGeometry(
            straightRoute(),
            decelerationMps2 = 3.0,
            speedVariance = 0.08,
            random = Random(7),
        )
        var identical = true
        for (i in varied.segmentSpeeds.indices) {
            val ratio = varied.segmentSpeeds[i] / base.segmentSpeeds[i]
            assertTrue(ratio in 0.92..1.08, "segment $i ratio $ratio")
            if (ratio != 1.0) identical = false
        }
        assertFalse(identical, "variance should change at least one segment")
        // ETA totals must derive from the varied speeds, not the originals.
        var expected = 0.0
        for (i in varied.segmentSpeeds.indices) expected += 100.0 / varied.segmentSpeeds[i]
        assertEquals(expected, varied.totalDurationSeconds, 1.0)
    }

    @Test
    fun `scaled speeds stay consistent with the minimum-speed clamp`() {
        // Base speed 0.6 m/s scaled by 1.5 would be 0.4 -> clamps to 0.5;
        // durations must follow the clamped speed, not the raw scale.
        val slowRoute = straightRoute().let { route ->
            route.copy(
                legs = listOf(
                    dev.mockarr.core.model.RouteLeg(
                        segmentDistancesMeters = List(10) { 100.0 },
                        segmentDurationsSeconds = List(10) { 100.0 / 0.6 },
                    ),
                ),
                durationSeconds = 1000.0 / 0.6,
            )
        }
        val geometry = RouteGeometry(slowRoute, decelerationMps2 = 3.0, durationScale = 1.5)
        assertEquals(0.5, geometry.segmentSpeeds[0], 0.001)
        assertEquals(1000.0 / 0.5, geometry.totalDurationSeconds, 1.0)
    }
}
