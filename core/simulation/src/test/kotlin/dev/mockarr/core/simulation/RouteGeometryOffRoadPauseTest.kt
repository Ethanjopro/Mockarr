package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.OffRoadSpan
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteGeometryOffRoadPauseTest {

    private val decel = 3.0
    private val pauseSeconds = 2

    /** Two 300 m legs of 100 m segments east along the equator at 10 m/s (7 vertices). */
    private fun route(spans: List<OffRoadSpan>, waits: List<Int> = emptyList()): Route {
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
            offRoadSpans = spans,
        )
    }

    private fun geometry(spans: List<OffRoadSpan>, pause: Int = pauseSeconds, waits: List<Int> = emptyList()) =
        RouteGeometry(route(spans, waits), decel, offRoadPauseSeconds = pause)

    @Test
    fun `an arrival connector pauses where the route leaves the road`() {
        val geometry = geometry(spans = listOf(OffRoadSpan(2, 4)))

        val stop = geometry.dwellStops.single()
        assertEquals(200.0, stop.distanceMeters, 1.0)
        assertEquals(pauseSeconds, stop.waitSeconds)
        assertEquals(-1, stop.waypointIndex)
        assertFalse(stop.isDestination)
        // The entry vertex is a hard zero so playback brakes into the pause.
        assertEquals(0.0, geometry.allowedVertexSpeeds[2])
    }

    @Test
    fun `a via stop pauses on arrival but not on departure`() {
        // Arrival span (2,3) ends at the stop vertex; departure span (3,4) starts there.
        val geometry = geometry(spans = listOf(OffRoadSpan(2, 3), OffRoadSpan(3, 4)))

        val stop = geometry.dwellStops.single()
        assertEquals(200.0, stop.distanceMeters, 1.0)
    }

    @Test
    fun `an off-road route start never pauses`() {
        assertTrue(geometry(spans = listOf(OffRoadSpan(0, 2))).dwellStops.isEmpty())
    }

    @Test
    fun `zero pause seconds leaves dwells untouched`() {
        val geometry = geometry(spans = listOf(OffRoadSpan(2, 4)), pause = 0, waits = listOf(0, 45, 0))

        assertEquals(listOf(1), geometry.dwellStops.map { it.waypointIndex })
    }

    @Test
    fun `out-of-range spans from stale persisted data are ignored`() {
        assertTrue(geometry(spans = listOf(OffRoadSpan(6, 8), OffRoadSpan(9, 12))).dwellStops.isEmpty())
    }

    @Test
    fun `pauses and waypoint waits merge in distance order`() {
        val geometry = geometry(spans = listOf(OffRoadSpan(4, 6)), waits = listOf(0, 45, 0))

        assertEquals(listOf(1, -1), geometry.dwellStops.map { it.waypointIndex })
        assertTrue(geometry.dwellStops[0].distanceMeters < geometry.dwellStops[1].distanceMeters)
    }
}
