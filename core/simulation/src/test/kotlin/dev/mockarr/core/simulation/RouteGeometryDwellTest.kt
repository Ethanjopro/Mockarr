package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteGeometryDwellTest {

    private val decel = 3.0

    /** Two 300 m legs of 100 m segments east along the equator at 10 m/s. */
    private fun twoLegRoute(waits: List<Int> = emptyList()): Route {
        val start = LatLng(0.0, 0.0)
        val points = (0..6).map { GeoMath.destination(start, 90.0, 100.0 * it) }
        val distances = List(3) { 100.0 }
        val leg = RouteLeg(distances, distances.map { it / 10.0 })
        return Route(
            points = points,
            legs = listOf(leg, leg),
            distanceMeters = 600.0,
            durationSeconds = 60.0,
            waypointWaitsSeconds = waits,
        )
    }

    @Test
    fun `mid waypoint wait becomes a dwell stop at the leg boundary`() {
        val geometry = RouteGeometry(twoLegRoute(waits = listOf(0, 45, 0)), decel)
        assertEquals(1, geometry.dwellStops.size)
        val stop = geometry.dwellStops.single()
        assertEquals(45, stop.waitSeconds)
        assertEquals(1, stop.waypointIndex)
        // Leg 0 spans 3 × 100 m of great-circle segments.
        assertEquals(300.0, stop.distanceMeters, 1.0)
        // The stop's vertex is a hard zero so playback brakes into it.
        assertEquals(0.0, geometry.allowedVertexSpeeds[3])
    }

    @Test
    fun `dwell stops carry their waypoint indices in order`() {
        val start = LatLng(0.0, 0.0)
        val points = (0..9).map { GeoMath.destination(start, 90.0, 100.0 * it) }
        val distances = List(3) { 100.0 }
        val leg = RouteLeg(distances, distances.map { it / 10.0 })
        val route = Route(
            points = points,
            legs = listOf(leg, leg, leg),
            distanceMeters = 900.0,
            durationSeconds = 90.0,
            waypointWaitsSeconds = listOf(0, 20, 30, 0),
        )
        val geometry = RouteGeometry(route, decel)
        assertEquals(listOf(1, 2), geometry.dwellStops.map { it.waypointIndex })
        assertEquals(listOf(20, 30), geometry.dwellStops.map { it.waitSeconds })
    }

    @Test
    fun `destination wait becomes a dwell stop at the last vertex`() {
        val geometry = RouteGeometry(twoLegRoute(waits = listOf(0, 0, 45)), decel)
        val stop = geometry.dwellStops.single()
        assertEquals(2, stop.waypointIndex)
        assertEquals(geometry.totalDistanceMeters, stop.distanceMeters, 1e-6)
    }

    @Test
    fun `misaligned waits list is ignored`() {
        val geometry = RouteGeometry(twoLegRoute(waits = listOf(45, 45)), decel)
        assertTrue(geometry.dwellStops.isEmpty())
    }

    @Test
    fun `empty waits leave vertex speeds untouched`() {
        val plain = RouteGeometry(twoLegRoute(), decel)
        val emptyWaits = RouteGeometry(twoLegRoute(waits = listOf(0, 0, 0)), decel)
        assertTrue(plain.dwellStops.isEmpty())
        assertTrue(emptyWaits.dwellStops.isEmpty())
        assertContentEquals(
            plain.allowedVertexSpeeds.toList(),
            emptyWaits.allowedVertexSpeeds.toList(),
        )
    }
}
