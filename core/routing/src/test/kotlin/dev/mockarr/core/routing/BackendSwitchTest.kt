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
    fun `managed mode uses the managed provider when it succeeds`() = runTest {
        val managed = FakeRoutes(Result.success(managedRoute))
        val public = FakeRoutes(Result.success(publicRoute))
        val switch = SwitchingRouteProvider(managed, public) { BackendMode.MANAGED }

        assertSame(managedRoute, switch.route(waypoints, RoutingProfile.DRIVING).getOrThrow())
        assertEquals(0, public.calls)
    }

    @Test
    fun `rate limit on the managed provider falls through to the public one`() = runTest {
        val managed = FakeRoutes(Result.failure(RoutingException.RateLimited()))
        val public = FakeRoutes(Result.success(publicRoute))
        val switch = SwitchingRouteProvider(managed, public) { BackendMode.MANAGED }

        assertSame(publicRoute, switch.route(waypoints, RoutingProfile.DRIVING).getOrThrow())
    }

    @Test
    fun `no route is a real answer and is not second-guessed`() = runTest {
        val managed = FakeRoutes(Result.failure(RoutingException.NoRoute()))
        val public = FakeRoutes(Result.success(publicRoute))
        val switch = SwitchingRouteProvider(managed, public) { BackendMode.MANAGED }

        assertIs<RoutingException.NoRoute>(switch.route(waypoints, RoutingProfile.DRIVING).exceptionOrNull())
        assertEquals(0, public.calls)
    }

    @Test
    fun `both failing reports the managed error`() = runTest {
        val managed = FakeRoutes(Result.failure(RoutingException.Server("managed down")))
        val public = FakeRoutes(Result.failure(RoutingException.RateLimited()))
        val switch = SwitchingRouteProvider(managed, public) { BackendMode.MANAGED }

        val error = switch.route(waypoints, RoutingProfile.DRIVING).exceptionOrNull()
        assertIs<RoutingException.Server>(error)
        assertEquals(1, public.calls)
    }

    @Test
    fun `public and custom modes, or no managed provider, skip the managed one`() = runTest {
        val managed = FakeRoutes(Result.success(managedRoute))
        val public = FakeRoutes(Result.success(publicRoute))

        val publicOnly = SwitchingRouteProvider(managed, public) { BackendMode.PUBLIC }
        val customOnly = SwitchingRouteProvider(managed, public) { BackendMode.CUSTOM }
        val noManaged = SwitchingRouteProvider(null, public) { BackendMode.MANAGED }
        assertSame(publicRoute, publicOnly.route(waypoints, RoutingProfile.DRIVING).getOrThrow())
        assertSame(publicRoute, customOnly.route(waypoints, RoutingProfile.DRIVING).getOrThrow())
        assertSame(publicRoute, noManaged.route(waypoints, RoutingProfile.DRIVING).getOrThrow())
        assertEquals(0, managed.calls)
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
        val switch = SwitchingGeocoder(managed, public) { BackendMode.MANAGED }

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
