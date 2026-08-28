package dev.mockarr.app.ui.screens

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MovedWaypointTest {

    private val stops = listOf(
        Waypoint(LatLng(48.0, 2.0)),
        Waypoint(LatLng(48.5, 2.5), waitSeconds = 300),
        Waypoint(LatLng(49.0, 3.0)),
    )

    @Test
    fun `relocates the stop and keeps its wait`() {
        val moved = stops.movedTo(1, LatLng(48.6, 2.6))

        assertEquals(Waypoint(LatLng(48.6, 2.6), waitSeconds = 300), moved[1])
        assertEquals(stops[0], moved[0])
        assertEquals(stops[2], moved[2])
    }

    @Test
    fun `ignores an index that no longer exists`() {
        assertSame(stops, stops.movedTo(3, LatLng(0.0, 0.0)))
        assertSame(stops, stops.movedTo(-1, LatLng(0.0, 0.0)))
    }
}
