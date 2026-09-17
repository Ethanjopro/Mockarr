package dev.mockarr.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapRouteShadeTest {

    @Test
    fun `seam sits just behind the progress`() {
        val (low, high) = shadeStops(0.5f)

        assertEquals(0.5f, high)
        assertTrue(low < high)
        assertTrue(high - low < 0.01f)
    }

    @Test
    fun `null progress puts the whole line ahead`() {
        val (low, high) = shadeStops(null)

        assertEquals(0f, low)
        assertTrue(high > low)
    }

    @Test
    fun `stops stay strictly increasing at both ends`() {
        listOf(-1f, 0f, 0.0001f, 0.999f, 1f, 2f).forEach { progress ->
            val (low, high) = shadeStops(progress)
            assertTrue(low < high, "progress $progress gave $low..$high")
            assertTrue(low >= 0f && high <= 1f, "progress $progress gave $low..$high")
        }
    }
}
