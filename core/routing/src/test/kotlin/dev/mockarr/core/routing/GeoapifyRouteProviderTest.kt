package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.RoutingProfile
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeoapifyRouteProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: GeoapifyRouteProvider

    private val waypoints = listOf(LatLng(48.8584, 2.2945), LatLng(48.8606, 2.3376))

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = GeoapifyRouteProvider(
            apiKey = "k",
            userAgent = "MockarrTest/0.0",
            baseUrl = server.url("/").toString(),
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `builds the documented request`() {
        val url = GeoapifyRouteProvider.routeUrl("https://api.geoapify.com/", "abc", waypoints, RoutingProfile.CYCLING)
        assertEquals(
            "https://api.geoapify.com/v1/routing?waypoints=48.8584,2.2945|48.8606,2.3376&mode=bicycle" +
                "&format=geojson&apiKey=abc",
            url,
        )
    }

    @Test
    fun `parses a geojson response and spreads step time over segments`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"type":"FeatureCollection","features":[{"type":"Feature",
                  "properties":{"mode":"drive","distance":3000,"time":300,
                    "waypoints":[{"location":[2.29455,48.85843],"original_index":0},{"location":[2.33762,48.86061],"original_index":1}],
                    "legs":[{"distance":3000,"time":300,
                      "steps":[{"from_index":0,"to_index":2,"distance":2000,"time":100},
                               {"from_index":2,"to_index":3,"distance":1000,"time":200}]}]},
                  "geometry":{"type":"MultiLineString","coordinates":[[[2.2945,48.8584],[2.3000,48.8584],[2.3200,48.8584],[2.3376,48.8606]]]}}]}
                """.trimIndent(),
            ),
        )

        val route = provider.route(waypoints, RoutingProfile.DRIVING).getOrThrow()

        assertEquals(4, route.points.size)
        assertEquals(3000.0, route.distanceMeters)
        assertEquals(300.0, route.durationSeconds)
        assertEquals(2, route.snappedWaypoints.size)
        val leg = route.legs.single()
        assertEquals(3, leg.segmentDistancesMeters.size)
        assertEquals(300.0, leg.segmentDurationsSeconds.sum(), absoluteTolerance = 1e-6)
        // Step 1 (100 s) covers segments 0..1 in proportion to their length; step 2 (200 s) is segment 2 alone.
        assertEquals(200.0, leg.segmentDurationsSeconds[2], absoluteTolerance = 1e-6)
        val step1Speed0 = leg.segmentDistancesMeters[0] / leg.segmentDurationsSeconds[0]
        val step1Speed1 = leg.segmentDistancesMeters[1] / leg.segmentDurationsSeconds[1]
        assertEquals(step1Speed0, step1Speed1, absoluteTolerance = 1e-6)
        assertTrue(server.takeRequest().path!!.contains("mode=drive"))
    }

    @Test
    fun `joins leg lines without duplicating the junction point`() {
        val a = LatLng(1.0, 1.0)
        val b = LatLng(2.0, 2.0)
        val c = LatLng(3.0, 3.0)
        assertEquals(listOf(a, b, c), GeoapifyRouteProvider.joinLegLines(listOf(listOf(a, b), listOf(b, c))))
    }

    @Test
    fun `segments no step claims fall back to the leg's mean speed`() {
        val line = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.01), LatLng(0.0, 0.02))
        val legData = GeoapifyLegData(distance = 2226.0, time = 100.0, steps = emptyList())
        val leg = GeoapifyRouteProvider.spreadSteps(line, legData)
        assertEquals(2, leg.segmentDurationsSeconds.size)
        assertEquals(100.0, leg.segmentDurationsSeconds.sum(), absoluteTolerance = 0.5)
    }

    @Test
    fun `maps 429 to RateLimited and 400 route errors to NoRoute`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setBody("""{"statusCode":429,"error":"Too Many Requests","message":"quota"}"""),
        )
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"statusCode":400,"error":"Bad Request","message":"Route not found"}"""),
        )

        assertIs<RoutingException.RateLimited>(provider.route(waypoints, RoutingProfile.DRIVING).exceptionOrNull())
        assertIs<RoutingException.NoRoute>(provider.route(waypoints, RoutingProfile.DRIVING).exceptionOrNull())
    }
}
