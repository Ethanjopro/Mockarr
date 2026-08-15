package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
