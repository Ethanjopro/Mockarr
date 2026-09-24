package dev.mockarr.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteLeadInTest {

    private val a = LatLng(32.7800, -96.8000)
    private val b = LatLng(32.7810, -96.8000)
    private val c = LatLng(32.7820, -96.8000)
    private val d = LatLng(32.7830, -96.8000)

    private fun routeOf(vararg points: LatLng, legs: Int = 1): Route {
        val distances = points.toList().zipWithNext { p, q -> GeoMath.distanceMeters(p, q) }
        val perLeg = distances.size / legs
        return Route(
            points = points.toList(),
            legs = distances.chunked(perLeg).map { RouteLeg(it, it.map { m -> m / 10.0 }) },
            distanceMeters = distances.sum(),
            durationSeconds = distances.sum() / 10.0,
        )
    }

    @Test
    fun `a seamless lead-in shares the route's first point`() {
        val planned = routeOf(b, c, d, legs = 2).copy(
            snappedWaypoints = listOf(b, c, d),
            waypointWaitsSeconds = listOf(0, 60, 0),
            waypointNames = listOf("Start St", "Museum", "Tower"),
        )
        val joined = planned.withLeadIn(routeOf(a, b))

        assertEquals(listOf(a, b, c, d), joined.points)
        assertEquals(3, joined.legs.size)
        assertEquals(joined.points.size - 1, joined.legs.sumOf { it.segmentDistancesMeters.size })
        assertEquals(listOf(a, b, c, d), joined.snappedWaypoints)
        assertEquals(listOf(0, 0, 60, 0), joined.waypointWaitsSeconds)
        assertEquals(listOf(null, "Start St", "Museum", "Tower"), joined.waypointNames)
    }

    @Test
    fun `a gap between the lead-in and the route is bridged by one connector segment`() {
        val planned = routeOf(c, d)
        val leadIn = routeOf(a, b)

        val joined = planned.withLeadIn(leadIn)

        assertEquals(listOf(a, b, c, d), joined.points)
        assertEquals(joined.points.size - 1, joined.legs.sumOf { it.segmentDistancesMeters.size })
        val gap = GeoMath.distanceMeters(b, c)
        assertEquals(leadIn.distanceMeters + gap + planned.distanceMeters, joined.distanceMeters, 0.001)
    }

    @Test
    fun `off-road spans of the planned route shift past the lead-in`() {
        val planned = routeOf(b, c, d).copy(offRoadSpans = listOf(OffRoadSpan(1, 2)))

        val joined = planned.withLeadIn(routeOf(a, b))

        assertEquals(listOf(OffRoadSpan(2, 3)), joined.offRoadSpans)
    }

    @Test
    fun `a lead-in without terrain rides level with the route's start`() {
        val planned = routeOf(b, c).copy(altitudes = listOf(150.0, 160.0))

        val joined = planned.withLeadIn(routeOf(a, b))

        assertEquals(listOf(150.0, 150.0, 160.0), joined.altitudes)
    }

    @Test
    fun `an unnamed route stays unnamed`() {
        val joined = routeOf(b, c).withLeadIn(routeOf(a, b))

        assertEquals(emptyList(), joined.waypointNames)
        assertNull(joined.altitudes)
    }
}
