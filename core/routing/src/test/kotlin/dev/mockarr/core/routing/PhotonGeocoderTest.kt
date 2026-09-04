package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotonGeocoderTest {

    private lateinit var server: MockWebServer
    private lateinit var geocoder: PhotonGeocoder

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        geocoder = PhotonGeocoder(userAgent = "MockarrTest/0.0", baseUrl = server.url("/").toString())
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `search url carries bias and rounded zoom only when given`() {
        val plain = PhotonGeocoder.searchUrl("https://photon.test/", "1 market st", null, null, 8)
        assertEquals("https://photon.test/api/?q=1+market+st&limit=8", plain)

        val biased = PhotonGeocoder.searchUrl("https://photon.test", "cafe", LatLng(37.42, -122.08), 15.6, 8)
        assertEquals("https://photon.test/api/?q=cafe&limit=8&lat=37.42&lon=-122.08&zoom=16", biased)
    }

    @Test
    fun `maps photon features to structured results`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"features": [
                  {"geometry": {"coordinates": [-122.08, 37.42]},
                   "properties": {"name": "Starbucks", "street": "Castro Street", "city": "Mountain View",
                                  "state": "California", "country": "United States",
                                  "osm_key": "amenity", "osm_value": "cafe"}},
                  {"geometry": {"coordinates": [-122.41, 37.77]},
                   "properties": {"housenumber": "1", "street": "Market Street", "city": "San Francisco",
                                  "state": "California", "osm_key": "building", "osm_value": "yes"}},
                  {"geometry": {"coordinates": [-122.27, 37.80]},
                   "properties": {"name": "Oakland", "state": "California", "country": "United States",
                                  "osm_key": "place", "osm_value": "city"}},
                  {"geometry": {"coordinates": [-122.44, 37.75]},
                   "properties": {"name": "25th Avenue", "city": "San Francisco", "state": "California",
                                  "osm_key": "highway", "osm_value": "residential"}},
                  {"properties": {"name": "no geometry"}}
                ]}
                """.trimIndent(),
            ),
        )

        val results = geocoder.search("x").getOrThrow()

        assertEquals(4, results.size)
        val (cafe, address, city) = results
        val street = results[3]
        assertEquals(PlaceKind.POI, cafe.kind)
        assertEquals("Castro Street, Mountain View, California", cafe.secondary)
        assertEquals("1 Market Street", address.name)
        assertEquals(PlaceKind.ADDRESS, address.kind)
        assertEquals("Market Street, San Francisco, California", address.secondary)
        assertEquals(PlaceKind.CITY, city.kind)
        assertEquals("California, United States", city.secondary)
        assertEquals(PlaceKind.STREET, street.kind)
        assertEquals("San Francisco, California", street.secondary)
        assertEquals(LatLng(37.42, -122.08), cafe.position)
    }

    @Test
    fun `search request passes bias and zoom to photon`() = runTest {
        server.enqueue(MockResponse().setBody("""{"features": []}"""))

        geocoder.search("cafe", bias = LatLng(37.42, -122.08), zoom = 14.2).getOrThrow()

        val path = server.takeRequest().path.orEmpty()
        assertTrue(path.contains("lat=37.42"), path)
        assertTrue(path.contains("lon=-122.08"), path)
        assertTrue(path.contains("zoom=14"), path)
    }

    @Test
    fun `malformed body is a failure not a crash`() = runTest {
        server.enqueue(MockResponse().setBody("not json"))

        assertTrue(geocoder.search("cafe").isFailure)
    }

    @Test
    fun `dedupe collapses same name in the same city or within 50 m`() {
        val a = GeocodingResult("Ukiah", LatLng(39.150, -123.207), city = "Ukiah")
        val sameCityOtherSpelling = GeocodingResult("ukiah", LatLng(39.160, -123.200), city = "ukiah")
        val nearbyNoCity = GeocodingResult("Ukiah", LatLng(39.1501, -123.2071), city = null)
        val farNoCity = GeocodingResult("Ukiah", LatLng(40.0, -123.0), city = null)
        val other = GeocodingResult("Willits", LatLng(39.409, -123.355), city = "Willits")
        val bridgeWay = GeocodingResult("Golden Gate Bridge", LatLng(37.82, -122.478), secondary = "California")
        val bridgeRelation = GeocodingResult("Golden Gate Bridge", LatLng(37.83, -122.479), secondary = "California")

        val kept = listOf(a, sameCityOtherSpelling, nearbyNoCity, farNoCity, other, bridgeWay, bridgeRelation).dedupe()

        assertEquals(listOf(a, farNoCity, other, bridgeWay), kept)
        assertFalse(kept.contains(sameCityOtherSpelling))
    }

    @Test
    fun `a feature with neither name nor street is dropped`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"features": [{"geometry": {"coordinates": [1.0, 2.0]}, "properties": {"city": "Nowhere"}}]}""",
            ),
        )

        assertNull(geocoder.search("x").getOrThrow().firstOrNull())
    }
}
