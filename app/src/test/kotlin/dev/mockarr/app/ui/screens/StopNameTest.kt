package dev.mockarr.app.ui.screens

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StopNameTest {

    private val a = LatLng(32.78, -96.80)
    private val b = LatLng(32.79, -96.80)
    private val route = Route(
        points = listOf(a, b),
        legs = listOf(RouteLeg(listOf(1_100.0), listOf(110.0))),
        distanceMeters = 1_100.0,
        durationSeconds = 110.0,
    )

    @Test
    fun `a finished lookup names the stop and the route`() {
        val state = MapViewModel.UiState(
            waypoints = listOf(Waypoint(a, name = "Reunion Tower"), Waypoint(b)),
            route = route,
        )

        val named = state.withStopName(b, "Elm Street")

        assertEquals(listOf("Reunion Tower", "Elm Street"), named.waypoints.map { it.name })
        assertEquals(listOf("Reunion Tower", "Elm Street"), named.route?.waypointNames)
    }

    @Test
    fun `a lookup never renames a stop that already has a name`() {
        val state = MapViewModel.UiState(waypoints = listOf(Waypoint(a, name = "Reunion Tower"), Waypoint(b)))

        assertSame(state, state.withStopName(a, "Elm Street"))
    }

    @Test
    fun `a loaded route brings its stop names back`() {
        val saved = route.copy(snappedWaypoints = listOf(a, b), waypointNames = listOf("Reunion Tower", null))

        val loaded = MapViewModel.UiState().withLoadedRoute(
            route = saved,
            profile = RoutingProfile.DRIVING,
            saved = true,
            trafficFactor = 1.0,
        )

        assertEquals(listOf("Reunion Tower", null), loaded.waypoints.map { it.name })
    }
}
