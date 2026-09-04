package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class BackendSwitchTest {

    private val waypoints = listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0))
    private val managedRoute = route(1.0)
    private val publicRoute = route(2.0)

    private class FakeRoutes(val result: Result<Route>) : RouteProvider {
        var calls = 0
        override suspend fun route(waypoints: List<LatLng>, profile: RoutingProfile): Result<Route> {
            calls++
            return result
        }
    }

    @Test
    fun `uses the managed provider when it succeeds`() = runTest {
        val managed = FakeRoutes(Result.success(managedRoute))
        val public = FakeRoutes(Result.success(publicRoute))
        val switch = SwitchingRouteProvider(managed, public)

        assertSame(managedRoute, switch.route(waypoints, RoutingProfile.DRIVING).getOrThrow())
        assertEquals(0, public.calls)
    }

    @Test
    fun `rate limit on the managed provider falls through to the public one`() = runTest {
        val managed = FakeRoutes(Result.failure(RoutingException.RateLimited()))
        val public = FakeRoutes(Result.success(publicRoute))
        val switch = SwitchingRouteProvider(managed, public)

        assertSame(publicRoute, switch.route(waypoints, RoutingProfile.DRIVING).getOrThrow())
    }

    @Test
    fun `no route from the managed provider still tries the public one`() = runTest {
        // The public OSRM snaps more leniently, so a stop Geoapify refuses may still route there.
        val managed = FakeRoutes(Result.failure(RoutingException.NoRoute()))
        val public = FakeRoutes(Result.success(publicRoute))
        val switch = SwitchingRouteProvider(managed, public)

        assertSame(publicRoute, switch.route(waypoints, RoutingProfile.DRIVING).getOrThrow())
        assertEquals(1, public.calls)
    }

    @Test
    fun `both failing reports the managed error`() = runTest {
        val managed = FakeRoutes(Result.failure(RoutingException.Server("managed down")))
        val public = FakeRoutes(Result.failure(RoutingException.RateLimited()))
        val switch = SwitchingRouteProvider(managed, public)

        val error = switch.route(waypoints, RoutingProfile.DRIVING).exceptionOrNull()
        assertIs<RoutingException.Server>(error)
        assertEquals(1, public.calls)
    }

    @Test
    fun `a definitive no route from the fallback wins over the managed error`() = runTest {
        val managed = FakeRoutes(Result.failure(RoutingException.Server("managed down")))
        val public = FakeRoutes(Result.failure(RoutingException.NoRoute()))
        val switch = SwitchingRouteProvider(managed, public)

        assertIs<RoutingException.NoRoute>(switch.route(waypoints, RoutingProfile.DRIVING).exceptionOrNull())
    }

    @Test
    fun `no managed provider goes straight to the public one`() = runTest {
        val public = FakeRoutes(Result.success(publicRoute))
        val switch = SwitchingRouteProvider(null, public)

        assertSame(publicRoute, switch.route(waypoints, RoutingProfile.DRIVING).getOrThrow())
    }

    @Test
    fun `geocoder falls through on any managed failure`() = runTest {
        val hit = GeocodingResult("Public hit", LatLng(1.0, 1.0))
        val managed = object : Geocoder {
            override suspend fun search(query: String, bias: LatLng?, zoom: Double?, limit: Int) =
                Result.failure<List<GeocodingResult>>(IllegalStateException("boom"))
            override suspend fun reverse(position: LatLng) = Result.failure<PlaceInfo>(IllegalStateException("boom"))
        }
        val public = object : Geocoder {
            override suspend fun search(
                query: String,
                bias: LatLng?,
                zoom: Double?,
                limit: Int,
            ) = Result.success(listOf(hit))
            override suspend fun reverse(position: LatLng) = Result.success(PlaceInfo("Street", "City"))
        }
        val switch = SwitchingGeocoder(managed, public)

        assertEquals(listOf(hit), switch.search("x").getOrThrow())
        assertEquals("Street", switch.reverse(LatLng(0.0, 0.0)).getOrThrow().name)
    }

    private fun route(marker: Double) = Route(
        points = listOf(LatLng(0.0, 0.0), LatLng(marker, marker)),
        legs = listOf(RouteLeg(listOf(1.0), listOf(1.0))),
        distanceMeters = 1.0,
        durationSeconds = 1.0,
    )
}
