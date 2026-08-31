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
    fun `uses snapped waypoints when the route was fetched for these stops`() {
        assertEquals(
            snapped.map { Waypoint(it) },
            displayWaypoints(raw, route(snapped), routeIsFallback = false, routedFor = rawPositions),
        )
    }

    @Test
    fun `preserves wait seconds when swapping in snapped positions`() {
        val withWait = listOf(raw[0].copy(waitSeconds = 300), raw[1])

        val displayed = displayWaypoints(withWait, route(snapped), routeIsFallback = false, routedFor = rawPositions)

        assertEquals(listOf(Waypoint(snapped[0], 300), Waypoint(snapped[1])), displayed)
    }

    @Test
    fun `falls back to raw taps for fallback routes`() {
        assertEquals(raw, displayWaypoints(raw, route(snapped), routeIsFallback = true, routedFor = rawPositions))
    }

    @Test
    fun `keeps existing stops snapped while a fetch for an added stop is in flight`() {
        val staleRoute = route(snapped)
        val newTap = Waypoint(LatLng(50.0, 4.0))

        assertEquals(
            listOf(Waypoint(snapped[0]), Waypoint(snapped[1]), newTap),
            displayWaypoints(raw + newTap, staleRoute, routeIsFallback = false, routedFor = rawPositions),
        )
    }

    @Test
    fun `keeps existing stops snapped when a new origin is prepended`() {
        val origin = Waypoint(LatLng(47.5, 1.5))

        assertEquals(
            listOf(origin, Waypoint(snapped[0]), Waypoint(snapped[1])),
            displayWaypoints(listOf(origin) + raw, route(snapped), routeIsFallback = false, routedFor = rawPositions),
        )
    }

    @Test
    fun `ignores a stale route of the same size after undo swaps the stops`() {
        // Undo restored different positions; the old route's snapped spots must not stick to them.
        val undone = listOf(Waypoint(LatLng(47.0, 1.0)), Waypoint(LatLng(46.0, 0.5)))

        assertEquals(
            undone,
            displayWaypoints(undone, route(snapped), routeIsFallback = false, routedFor = rawPositions),
        )
    }

    @Test
    fun `keeps a dragged stop at the finger while the others stay snapped`() {
        val dragTarget = LatLng(48.5, 2.5)
        val dragging = listOf(raw[0], Waypoint(dragTarget))

        val displayed = displayWaypoints(dragging, route(snapped), routeIsFallback = false, routedFor = rawPositions)

        assertEquals(listOf(Waypoint(snapped[0]), Waypoint(dragTarget)), displayed)
    }

    @Test
    fun `falls back to raw taps without a route, snapped data or request positions`() {
        assertEquals(raw, displayWaypoints(raw, route = null, routeIsFallback = false, routedFor = rawPositions))
        assertEquals(raw, displayWaypoints(raw, route(emptyList()), routeIsFallback = false, routedFor = rawPositions))
        assertEquals(raw, displayWaypoints(raw, route(snapped), routeIsFallback = false, routedFor = null))
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
