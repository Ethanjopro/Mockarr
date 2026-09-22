package dev.mockarr.app.ui.screens

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoadedRouteStateTest {

    private val a = LatLng(48.0, 2.0)
    private val b = LatLng(48.5, 2.5)
    private val c = LatLng(49.0, 3.0)

    private fun route(snapped: List<LatLng>, waits: List<Int> = emptyList()) = Route(
        points = listOf(a, b, c),
        legs = emptyList(),
        distanceMeters = 1_000.0,
        durationSeconds = 60.0,
        snappedWaypoints = snapped,
        waypointWaitsSeconds = waits,
    )

    @Test
    fun `stops come from the snapped waypoints with their waits`() {
        val state = MapViewModel.UiState(isRouting = true)
            .withLoadedRoute(route(listOf(a, b, c), waits = listOf(0, 300)), RoutingProfile.WALKING, false, 1.2)
        assertEquals(listOf(Waypoint(a), Waypoint(b, 300), Waypoint(c)), state.waypoints)
        assertEquals(listOf(a, b, c), state.routedFor)
        assertEquals(RoutingProfile.WALKING, state.profile)
        assertFalse(state.isRouting)
        assertFalse(state.routeSaved)
        assertEquals(1.2, state.trafficFactor)
    }

    @Test
    fun `no snapped waypoints falls back to the route's ends`() {
        val state = MapViewModel.UiState().withLoadedRoute(route(emptyList()), RoutingProfile.DRIVING, true, 1.0)
        assertEquals(listOf(Waypoint(a), Waypoint(c)), state.waypoints)
        assertTrue(state.routeSaved)
    }
}
