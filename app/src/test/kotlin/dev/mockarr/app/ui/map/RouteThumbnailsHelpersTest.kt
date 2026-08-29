package dev.mockarr.app.ui.map

import dev.mockarr.app.ui.theme.MapPalette
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
        palette = testPalette(route = 0xFF3949AB.toInt()),
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
    fun `cache file name changes with the palette so a theme switch re-renders`() {
        val other = spec.copy(palette = testPalette(route = 0xFF9FA8FF.toInt()))

        assertTrue(cacheFileName(spec) != cacheFileName(other))
    }

    @Test
    fun `route id round-trips through the cache file name`() {
        assertEquals(7L, routeIdOf(cacheFileName(spec)))
        assertNull(routeIdOf("garbage.png"))
    }
}

private fun testPalette(route: Int) = MapPalette(
    route = route,
    routeCasing = 0xFFFFFFFF.toInt(),
    fallbackRoute = 0xFFB8741A.toInt(),
    position = route,
    positionRing = 0xFFFFFFFF.toInt(),
    holdPin = 0xFFE0901E.toInt(),
    stopStart = 0xFF1E8A5A.toInt(),
    stopVia = route,
    stopEnd = 0xFF1A1B21.toInt(),
    stopText = 0xFFFFFFFF.toInt(),
    stopRing = 0xFFFFFFFF.toInt(),
    selection = 0xFF5C6BC0.toInt(),
    chip = 0xF21A1B21.toInt(),
    chipText = 0xFFFFFFFF.toInt(),
    chipActive = 0xFFF0A422.toInt(),
    chipActiveText = 0xFF2B1A00.toInt(),
)
