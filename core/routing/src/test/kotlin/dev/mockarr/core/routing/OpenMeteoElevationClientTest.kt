package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenMeteoElevationClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenMeteoElevationClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenMeteoElevationClient(
            userAgent = "test",
            baseUrl = server.url("/").toString(),
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun elevationBody(count: Int, offset: Double = 0.0): String =
        (0 until count).joinToString(",", prefix = "{\"elevation\":[", postfix = "]}") {
            (it + offset).toString()
        }

    @Test
    fun `parses elevations in order`() = runTest {
        server.enqueue(MockResponse().setBody("""{"elevation":[12.5, 99.0]}"""))
        val result = client.elevations(listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)))
        assertEquals(listOf(12.5, 99.0), result.getOrThrow())
        val path = server.takeRequest().path.orEmpty()
        assertTrue("latitude=1.00000,3.00000" in path, path)
        assertTrue("longitude=2.00000,4.00000" in path, path)
    }

    @Test
    fun `chunks large batches into multiple requests`() = runTest {
        server.enqueue(MockResponse().setBody(elevationBody(100)))
        server.enqueue(MockResponse().setBody(elevationBody(50, offset = 100.0)))
        val coords = List(150) { LatLng(it * 0.01, 0.0) }
        val result = client.elevations(coords).getOrThrow()
        assertEquals(150, result.size)
        assertEquals(2, server.requestCount)
        assertEquals(0.0, result.first(), 0.001)
        assertEquals(149.0, result.last(), 0.001)
    }

    @Test
    fun `http errors surface as failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = client.elevations(listOf(LatLng(1.0, 2.0)))
        assertTrue(result.isFailure)
    }
}
