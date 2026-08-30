package dev.mockarr.app.ui.screens

import dev.mockarr.core.data.RecentSearch
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.routing.GeocodingResult
import dev.mockarr.core.routing.PlaceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapSearchLogicTest {

    private val mountainView = LatLng(37.386, -122.084)
    private val ukiah = LatLng(39.150, -123.208)
    private val sanFrancisco = LatLng(37.775, -122.418)

    @Test
    fun `anchor prefers the mocked position then the camera then the real fix`() {
        assertEquals(mountainView, searchAnchor(mocked = mountainView, camera = sanFrancisco, real = ukiah))
        assertEquals(sanFrancisco, searchAnchor(mocked = null, camera = sanFrancisco, real = ukiah))
        assertEquals(ukiah, searchAnchor(mocked = null, camera = null, real = ukiah))
        assertNull(searchAnchor(null, null, null))
    }

    @Test
    fun `bias zoom caps a street-level camera at a city view`() {
        assertEquals(12.0, biasZoom(16.4))
        assertEquals(11.0, biasZoom(11.0))
        assertEquals(8.0, biasZoom(5.0))
        assertNull(biasZoom(null))
    }

    @Test
    fun `prefix cache narrows the longest cached prefix to names still matching`() {
        val cache = PrefixCache()
        val starbucks = GeocodingResult("Starbucks", mountainView)
        val stanford = GeocodingResult("Stanford", sanFrancisco)
        cache.put("st", listOf(GeocodingResult("Stinson Beach", sanFrancisco)))
        cache.put("sta", listOf(starbucks, stanford))

        assertEquals(listOf(starbucks), cache.lookup("Starb"))
        assertEquals(listOf(starbucks, stanford), cache.lookup(" sta "))
        assertNull(cache.lookup("oak"))
    }

    @Test
    fun `prefix cache evicts the least recently used query`() {
        val cache = PrefixCache(capacity = 2)
        cache.put("a", listOf(GeocodingResult("A", mountainView)))
        cache.put("b", listOf(GeocodingResult("B", mountainView)))
        cache.lookup("a")
        cache.put("c", listOf(GeocodingResult("C", mountainView)))

        assertNull(cache.lookup("b"))
        assertEquals(1, cache.lookup("a")?.size)
    }

    @Test
    fun `recents match by prefix and all show for an empty query`() {
        val recents = listOf(recent("Starbucks", mountainView, 2), recent("Oakland", ukiah, 1))

        assertEquals(recents, matchingRecents(recents, ""))
        assertEquals(listOf(recents[0]), matchingRecents(recents, "sta"))
        assertTrue(matchingRecents(recents, "ukiah").isEmpty())
    }

    @Test
    fun `merge puts recents first and drops the same place from the hits`() {
        val recents = listOf(recent("Starbucks", mountainView, 1))
        val samePlaceAgain = GeocodingResult("starbucks", LatLng(37.3861, -122.0841), kind = PlaceKind.POI)
        val otherStarbucks = GeocodingResult("Starbucks", sanFrancisco, kind = PlaceKind.POI)

        val merged = mergeSuggestions(recents, listOf(samePlaceAgain, otherStarbucks), anchor = mountainView)

        assertEquals(listOf(true, false), merged.map { it.recent })
        assertEquals(listOf(mountainView, sanFrancisco), merged.map { it.result.position })
        assertEquals(0.0, merged[0].distanceMeters)
        assertTrue((merged[1].distanceMeters ?: 0.0) > 40_000.0)
    }

    private fun recent(name: String, at: LatLng, millis: Long) = RecentSearch(
        name = name,
        kind = PlaceKind.POI,
        latitude = at.latitude,
        longitude = at.longitude,
        pickedAtMillis = millis,
    )
}
