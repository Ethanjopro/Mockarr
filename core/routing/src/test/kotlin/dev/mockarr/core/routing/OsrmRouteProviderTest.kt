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

class OsrmRouteProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: OsrmRouteProvider

    private val waypoints = listOf(LatLng(48.8584, 2.2945), LatLng(48.8606, 2.3376))

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = OsrmRouteProvider(
            baseUrlProvider = { server.url("/").toString() },
            userAgent = "MockarrTest/0.0",
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses a successful response`() = runTest {
        val geometry = Polyline6.encode(
            listOf(LatLng(48.8584, 2.2945), LatLng(48.8590, 2.3100), LatLng(48.8606, 2.3376)),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "code": "Ok",
                  "routes": [{
                    "distance": 3300.5,
                    "duration": 540.2,
                    "geometry": "${geometry.replace("\\", "\\\\").replace("\"", "\\\"")}",
                    "legs": [{
                      "annotation": {
                        "distance": [1500.1, 1800.4],
                        "duration": [240.0, 300.2]
                      }
                    }]
                  }]
                }
                """.trimIndent(),
            ),
        )

        val route = provider.route(waypoints, RoutingProfile.DRIVING).getOrThrow()

        assertEquals(3, route.points.size)
        assertEquals(3300.5, route.distanceMeters, 0.001)
        assertEquals(540.2, route.durationSeconds, 0.001)
        assertEquals(listOf(1500.1, 1800.4), route.legs.single().segmentDistancesMeters)

        val request = server.takeRequest()
        val path = request.path.orEmpty()
        assertTrue(path.startsWith("/route/v1/driving/2.2945,48.8584;2.3376,48.8606"))
        assertTrue(path.contains("geometries=polyline6"))
        assertTrue(path.contains("annotations=distance%2Cduration") || path.contains("annotations=distance,duration"))
        assertEquals("MockarrTest/0.0", request.getHeader("User-Agent"))
    }

    @Test
    fun `maps HTTP 429 to RateLimited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"code":"TooBusy"}"""))
        val error = provider.route(waypoints, RoutingProfile.DRIVING).exceptionOrNull()
        assertIs<RoutingException.RateLimited>(error)
    }

    @Test
    fun `maps HTTP 400 NoRoute body to NoRoute`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"NoRoute"}"""))
        val error = provider.route(waypoints, RoutingProfile.DRIVING).exceptionOrNull()
        assertIs<RoutingException.NoRoute>(error)
    }

    @Test
    fun `maps HTTP 500 to Server`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val error = provider.route(waypoints, RoutingProfile.DRIVING).exceptionOrNull()
        assertIs<RoutingException.Server>(error)
    }

    @Test
    fun `maps connection failure to Network`() = runTest {
        server.shutdown()
        val error = provider.route(waypoints, RoutingProfile.DRIVING).exceptionOrNull()
        assertIs<RoutingException.Network>(error)
    }
}
