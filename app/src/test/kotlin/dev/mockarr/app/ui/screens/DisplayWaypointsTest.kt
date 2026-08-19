package dev.mockarr.app.ui.screens

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayWaypointsTest {

    private val rawPositions = listOf(LatLng(48.0, 2.0), LatLng(49.0, 3.0))
    private val raw = rawPositions.map { Waypoint(it) }
    private val snapped = listOf(LatLng(48.0001, 2.0001), LatLng(49.0001, 3.0001))

    private fun route(snappedWaypoints: List<LatLng>) = Route(
        points = rawPositions,
        legs = emptyList(),
        distanceMeters = 1000.0,
        durationSeconds = 60.0,
        snappedWaypoints = snappedWaypoints,
    )

    @Test
    fun `uses snapped waypoints when they match the request`() {
        assertEquals(
            snapped.map { Waypoint(it) },
            displayWaypoints(raw, route(snapped), routeIsFallback = false),
        )
    }

    @Test
    fun `preserves wait seconds when swapping in snapped positions`() {
        val withWait = listOf(raw[0].copy(waitSeconds = 300), raw[1])

        val displayed = displayWaypoints(withWait, route(snapped), routeIsFallback = false)

        assertEquals(listOf(Waypoint(snapped[0], 300), Waypoint(snapped[1])), displayed)
    }

    @Test
    fun `falls back to raw taps for fallback routes`() {
        assertEquals(raw, displayWaypoints(raw, route(snapped), routeIsFallback = true))
    }

    @Test
    fun `falls back to raw taps while a fetch is in flight`() {
        val staleRoute = route(snapped)
        val moreTaps = raw + Waypoint(LatLng(50.0, 4.0))

        assertEquals(moreTaps, displayWaypoints(moreTaps, staleRoute, routeIsFallback = false))
    }

    @Test
    fun `falls back to raw taps without a route or snapped data`() {
        assertEquals(raw, displayWaypoints(raw, route = null, routeIsFallback = false))
        assertEquals(raw, displayWaypoints(raw, route(emptyList()), routeIsFallback = false))
    }

    @Test
    fun `withWaits attaches aligned waits and ignores misaligned ones`() {
        val leg = RouteLeg(listOf(100.0), listOf(10.0))
        val base = route(snapped).copy(legs = listOf(leg))
        val waypoints = listOf(raw[0].copy(waitSeconds = 60), raw[1])

        assertEquals(listOf(60, 0), base.withWaits(waypoints).waypointWaitsSeconds)
        assertEquals(emptyList(), base.withWaits(waypoints + raw[0]).waypointWaitsSeconds)
    }
}
