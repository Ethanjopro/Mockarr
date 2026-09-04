package dev.mockarr.core.routing

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OffRoadStitcherTest {

    private val roadA = LatLng(48.0, 2.0)
    private val roadB = GeoMath.destination(roadA, 90.0, 1000.0)
    private val roadC = GeoMath.destination(roadB, 90.0, 1000.0)

    /** A→B→C along the road, one segment per leg, snapped exactly to the request. */
    private fun roadRoute(snapped: List<LatLng> = listOf(roadA, roadB, roadC)) = Route(
        points = listOf(roadA, roadB, roadC),
        legs = listOf(
            RouteLeg(listOf(1000.0), listOf(72.0)),
            RouteLeg(listOf(1000.0), listOf(72.0)),
        ),
        distanceMeters = 2000.0,
        durationSeconds = 144.0,
        snappedWaypoints = snapped,
    )

    private fun Route.assertLegsTileGeometry() {
        assertEquals(points.size - 1, legs.sumOf { it.segmentDistancesMeters.size })
        legs.forEach { assertEquals(it.segmentDistancesMeters.size, it.segmentDurationsSeconds.size) }
    }

    @Test
    fun `returns the same route when every stop is on the road`() {
        val route = roadRoute()
        assertSame(route, stitchOffRoad(route, listOf(roadA, roadB, roadC), RoutingProfile.DRIVING))
    }

    @Test
    fun `returns the same route when the request does not match the geometry`() {
        val route = roadRoute()
        assertSame(route, stitchOffRoad(route, listOf(roadA, roadC), RoutingProfile.DRIVING))
    }

    @Test
    fun `splices a dotted tail to an off-road destination`() {
        val target = GeoMath.destination(roadC, 0.0, 400.0)

        val stitched = stitchOffRoad(roadRoute(), listOf(roadA, roadB, target), RoutingProfile.DRIVING)

        stitched.assertLegsTileGeometry()
        assertEquals(target, stitched.points.last())
        assertEquals(target, stitched.snappedWaypoints.last())
        assertEquals(2, stitched.legs.size)
        assertEquals(1, stitched.offRoadSpans.size)
        val span = stitched.offRoadSpans.single()
        assertEquals(roadC, stitched.points[span.start])
        assertEquals(stitched.points.lastIndex, span.end)
        assertEquals(2400.0, stitched.distanceMeters, 1.0)
        // The connector runs at half the profile's typical speed.
        assertEquals(144.0 + 400.0 / (13.9 * 0.5), stitched.durationSeconds, 1.0)
    }

    @Test
    fun `stitches a Geoapify-shaped route whose snaps come from the geometry ends`() {
        // Regression: Geoapify echoes the request as its waypoints, so the provider must
        // derive the snaps from the leg lines or a park-interior stop is never stitched.
        val target = GeoMath.destination(roadC, 0.0, 100.0)
        val legLines = listOf(listOf(roadA, roadB), listOf(roadB, roadC))
        val route = roadRoute(snapped = GeoapifyRouteProvider.snappedWaypoints(legLines))

        val stitched = stitchOffRoad(route, listOf(roadA, roadB, target), RoutingProfile.DRIVING)

        stitched.assertLegsTileGeometry()
        assertEquals(1, stitched.offRoadSpans.size)
        assertEquals(target, stitched.points.last())
    }

    @Test
    fun `an off-road via stop gets an out-and-back spur inside its legs`() {
        val target = GeoMath.destination(roadB, 0.0, 300.0)

        val stitched = stitchOffRoad(roadRoute(), listOf(roadA, target, roadC), RoutingProfile.DRIVING)

        stitched.assertLegsTileGeometry()
        assertEquals(2, stitched.legs.size)
        assertEquals(2, stitched.offRoadSpans.size)
        assertEquals(target, stitched.snappedWaypoints[1])
        // The waypoint boundary (end of leg 0) sits AT the marker, so a dwell happens there.
        val boundary = stitched.legs[0].segmentDistancesMeters.size
        assertEquals(target, stitched.points[boundary])
        // Out and back: roughly 600 m of extra distance.
        assertEquals(2600.0, stitched.distanceMeters, 2.0)
    }

    @Test
    fun `an off-road start begins the route at the marker`() {
        val target = GeoMath.destination(roadA, 180.0, 250.0)

        val stitched = stitchOffRoad(roadRoute(), listOf(target, roadB, roadC), RoutingProfile.WALKING)

        stitched.assertLegsTileGeometry()
        assertEquals(target, stitched.points.first())
        assertEquals(target, stitched.snappedWaypoints.first())
        assertEquals(0, stitched.offRoadSpans.single().start)
    }

    @Test
    fun `walking pace times connectors at walking speed`() {
        val target = GeoMath.destination(roadC, 0.0, 400.0)

        val stitched =
            stitchOffRoad(roadRoute(), listOf(roadA, roadB, target), RoutingProfile.DRIVING, walkingPace = true)

        // The connector crosses at 1.4 m/s regardless of the driving profile.
        assertEquals(144.0 + 400.0 / 1.4, stitched.durationSeconds, 1.0)
        val span = stitched.offRoadSpans.single()
        val connectorSeconds = stitched.legs.last().segmentDurationsSeconds.takeLast(span.end - span.start).sum()
        assertEquals(400.0 / 1.4, connectorSeconds, 1.0)
    }

    @Test
    fun `a small kerb-distance snap is left alone`() {
        val nearby = GeoMath.destination(roadC, 0.0, 10.0)
        val route = roadRoute(snapped = listOf(roadA, roadB, roadC))

        assertSame(route, stitchOffRoad(route, listOf(roadA, roadB, nearby), RoutingProfile.DRIVING))
    }

    @Test
    fun `connector geometry is dense enough for playback`() {
        val target = GeoMath.destination(roadC, 0.0, 400.0)

        val stitched = stitchOffRoad(roadRoute(), listOf(roadA, roadB, target), RoutingProfile.DRIVING)

        val span = stitched.offRoadSpans.single()
        // ~50 m steps over 400 m: several interpolated points, none further apart than ~51 m.
        assertTrue(span.end - span.start >= 7)
        for (i in span.start until span.end) {
            assertTrue(GeoMath.distanceMeters(stitched.points[i], stitched.points[i + 1]) <= 51.0)
        }
    }
}
