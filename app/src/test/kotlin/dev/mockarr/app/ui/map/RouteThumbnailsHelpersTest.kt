package dev.mockarr.app.ui.map

import dev.mockarr.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteThumbnailsHelpersTest {

    private val spec = ThumbSpec(
        routeId = 7,
        createdAtEpochMillis = 1_723_000_000_000,
        encodedPolyline6 = "irrelevant",
        styleUrl = "https://tiles.openfreemap.org/styles/liberty",
        sizePx = 264,
        density = 3f,
    )

    @Test
    fun `pads the route bounding box on every side`() {
        val bounds = paddedBounds(listOf(LatLng(48.80, 2.20), LatLng(48.90, 2.40)))

        assertTrue(bounds.minLat < 48.80)
        assertTrue(bounds.maxLat > 48.90)
        assertTrue(bounds.minLng < 2.20)
        assertTrue(bounds.maxLng > 2.40)
    }

    @Test
    fun `enforces a minimum span for very short routes`() {
        val bounds = paddedBounds(listOf(LatLng(48.85840, 2.29450), LatLng(48.85841, 2.29451)))

        assertTrue(bounds.maxLat - bounds.minLat >= 0.004 - 1e-9)
        assertTrue(bounds.maxLng - bounds.minLng >= 0.004 - 1e-9)
    }

    @Test
    fun `cache file name changes with style and size but not with density`() {
        val base = cacheFileName(spec)

        assertEquals(base, cacheFileName(spec.copy(density = 1f)))
        assertTrue(base != cacheFileName(spec.copy(styleUrl = "https://tiles.openfreemap.org/styles/dark")))
        assertTrue(base != cacheFileName(spec.copy(sizePx = 176)))
    }

    @Test
    fun `route id round-trips through the cache file name`() {
        assertEquals(7L, routeIdOf(cacheFileName(spec)))
        assertNull(routeIdOf("garbage.png"))
    }
}
