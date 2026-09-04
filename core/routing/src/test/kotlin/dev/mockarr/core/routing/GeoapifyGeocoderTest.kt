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

class GeoapifyGeocoderTest {

    private lateinit var server: MockWebServer
    private lateinit var geocoder: GeoapifyGeocoder

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        geocoder = GeoapifyGeocoder(apiKey = "k", userAgent = "MockarrTest/0.0", baseUrl = server.url("/").toString())
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `builds the documented autocomplete and reverse urls`() {
        assertEquals(
            "https://api.geoapify.com/v1/geocode/autocomplete?text=1+market+st&limit=8&format=json" +
                "&bias=proximity:-122.08,37.42&apiKey=abc",
            GeoapifyGeocoder.searchUrl("https://api.geoapify.com/", "abc", "1 market st", LatLng(37.42, -122.08), 8),
        )
        assertEquals(
            "https://api.geoapify.com/v1/geocode/reverse?lat=37.42&lon=-122.08&format=json&apiKey=abc",
            GeoapifyGeocoder.reverseUrl("https://api.geoapify.com", "abc", LatLng(37.42, -122.08)),
        )
    }

    @Test
    fun `maps autocomplete hits onto results with kinds and dedupes`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"results":[
                  {"name":"Kaiser Permanente","street":"Veterans Boulevard","city":"Redwood City","state":"California","country":"United States",
                   "result_type":"amenity","category":"healthcare.hospital","lat":37.49,"lon":-122.23},
                  {"name":"Kaiser Permanente","street":"Veterans Boulevard","city":"Redwood City","state":"California","country":"United States",
                   "result_type":"amenity","category":"healthcare.hospital","lat":37.4901,"lon":-122.2301},
                  {"housenumber":"1150","street":"Veterans Boulevard","city":"Redwood City","state":"California","country":"United States",
                   "address_line1":"1150 Veterans Boulevard","result_type":"building","lat":37.48,"lon":-122.22},
                  {"name":"Redwood City","state":"California","country":"United States","result_type":"city","lat":37.485,"lon":-122.236},
                  {"name":"nowhere","result_type":"unknown"}
                ]}
                """.trimIndent(),
            ),
        )

        val results = geocoder.search("kaiser", bias = LatLng(37.4, -122.2)).getOrThrow()

        assertEquals(3, results.size)
        assertEquals(PlaceKind.POI, results[0].kind)
        assertEquals("Veterans Boulevard, Redwood City, California", results[0].secondary)
        assertEquals("1150 Veterans Boulevard", results[1].name)
        assertEquals(PlaceKind.ADDRESS, results[1].kind)
        assertEquals(PlaceKind.CITY, results[2].kind)
        assertTrue(server.takeRequest().path!!.contains("bias=proximity:-122.2,37.4"))
    }

    @Test
    fun `reverse returns street-level place info`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"results":[
                  {"street":"Veterans Boulevard","city":"Redwood City","result_type":"street","lat":37.49,"lon":-122.23}
                ]}
                """.trimIndent(),
            ),
        )

        val info = geocoder.reverse(LatLng(37.49, -122.23)).getOrThrow()

        assertEquals("Veterans Boulevard", info.name)
        assertEquals("Redwood City", info.city)
        assertEquals(PlaceKind.STREET, info.kind)
    }

    @Test
    fun `http errors surface as failures`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(geocoder.search("x").isFailure)
    }
}
