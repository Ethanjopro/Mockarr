package dev.mockarr.app.ui.screens

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayWaypointsTest {

    private val raw = listOf(LatLng(48.0, 2.0), LatLng(49.0, 3.0))
    private val snapped = listOf(LatLng(48.0001, 2.0001), LatLng(49.0001, 3.0001))

    private fun route(snappedWaypoints: List<LatLng>) = Route(
        points = raw,
        legs = emptyList(),
        distanceMeters = 1000.0,
        durationSeconds = 60.0,
        snappedWaypoints = snappedWaypoints,
    )

    @Test
    fun `uses snapped waypoints when they match the request`() {
        assertEquals(snapped, displayWaypoints(raw, route(snapped), routeIsFallback = false))
    }

    @Test
    fun `falls back to raw taps for fallback routes`() {
        assertEquals(raw, displayWaypoints(raw, route(snapped), routeIsFallback = true))
    }

    @Test
    fun `falls back to raw taps while a fetch is in flight`() {
        val staleRoute = route(snapped)
        val moreTaps = raw + LatLng(50.0, 4.0)

        assertEquals(moreTaps, displayWaypoints(moreTaps, staleRoute, routeIsFallback = false))
    }

    @Test
    fun `falls back to raw taps without a route or snapped data`() {
        assertEquals(raw, displayWaypoints(raw, route = null, routeIsFallback = false))
        assertEquals(raw, displayWaypoints(raw, route(emptyList()), routeIsFallback = false))
    }
}
