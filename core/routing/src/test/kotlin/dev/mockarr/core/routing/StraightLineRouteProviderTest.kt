package dev.mockarr.core.routing

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.RoutingProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StraightLineRouteProviderTest {

    private val provider = StraightLineRouteProvider()

    @Test
    fun `total distance matches great-circle distance`() = runTest {
        val from = LatLng(48.8584, 2.2945)
        val to = LatLng(48.8606, 2.3376)
        val route = provider.route(listOf(from, to), RoutingProfile.DRIVING).getOrThrow()
        val direct = GeoMath.distanceMeters(from, to)
        assertEquals(direct, route.distanceMeters, direct * 0.01)
        assertEquals(from, route.points.first())
        assertEquals(to, route.points.last())
    }

    @Test
    fun `interpolates dense points roughly every 50 m`() = runTest {
        val from = LatLng(0.0, 0.0)
        val to = LatLng(0.0, 0.01) // ~1113 m along the equator
        val route = provider.route(listOf(from, to), RoutingProfile.WALKING).getOrThrow()
        assertTrue(route.points.size in 20..25, "expected ~22 points, got ${route.points.size}")
        // Segments should each be around 50 m
        route.legs.single().segmentDistancesMeters.forEach { d ->
            assertTrue(d in 40.0..60.0, "segment of $d m")
        }
    }

    @Test
    fun `duration uses profile speed`() = runTest {
        val route = provider.route(
            listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.01)),
            RoutingProfile.WALKING,
        ).getOrThrow()
        assertEquals(route.distanceMeters / 1.4, route.durationSeconds, 1.0)
    }
}
