package dev.mockarr.app.ui.screens

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapDraftTest {

    private val a = LatLng(32.77, -96.80)
    private val b = LatLng(32.78, -96.81)
    private val c = LatLng(32.79, -96.82)
    private val route = Route(points = listOf(a, b), legs = emptyList(), distanceMeters = 900.0, durationSeconds = 90.0)

    private val built = MapViewModel.UiState(
        waypoints = listOf(Waypoint(a, name = "Main St"), Waypoint(b, waitSeconds = 60)),
        profile = RoutingProfile.CYCLING,
        route = route,
        routedFor = listOf(a, b),
        routeSaved = true,
    )

    @Test
    fun `a map with no stops keeps no draft`() {
        assertNull(MapViewModel.UiState().toDraft())
    }

    @Test
    fun `a kept draft restores the stops, mode, route and saved flag`() {
        val restored = MapViewModel.UiState().withDraft(built.toDraft()!!)
        assertEquals(built.waypoints, restored.waypoints)
        assertEquals(RoutingProfile.CYCLING, restored.profile)
        assertEquals(route, restored.route)
        assertEquals(listOf(a, b), restored.routedFor)
        assertTrue(restored.routeSaved)
    }

    @Test
    fun `a route drawn for other stops is dropped for a refetch`() {
        val edited = built.copy(waypoints = built.waypoints + Waypoint(c))
        val restored = MapViewModel.UiState().withDraft(edited.toDraft()!!)
        assertEquals(3, restored.waypoints.size)
        assertNull(restored.route)
        assertNull(restored.routedFor)
        assertFalse(restored.routeSaved)
    }
}
