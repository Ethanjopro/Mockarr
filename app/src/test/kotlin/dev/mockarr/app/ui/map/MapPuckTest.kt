package dev.mockarr.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class MapPuckTest {

    @Test
    fun `bearing takes the short way round through north`() {
        assertEquals(0.0, lerpBearing(350.0, 10.0, 0.5), 1e-9)
        assertEquals(355.0, lerpBearing(350.0, 10.0, 0.25), 1e-9)
        assertEquals(5.0, lerpBearing(350.0, 10.0, 0.75), 1e-9)
    }

    @Test
    fun `bearing takes the short way round the other direction too`() {
        assertEquals(0.0, lerpBearing(10.0, 350.0, 0.5), 1e-9)
        assertEquals(350.0, lerpBearing(10.0, 350.0, 1.0), 1e-9)
    }

    @Test
    fun `bearing lerp is plain interpolation when no wrap is involved`() {
        assertEquals(90.0, lerpBearing(45.0, 135.0, 0.5), 1e-9)
        assertEquals(45.0, lerpBearing(45.0, 135.0, 0.0), 1e-9)
    }

    @Test
    fun `glide follows the fix interval within its clamp`() {
        assertEquals(1_000, glideMillis(1_000))
        assertEquals(500, glideMillis(500))
        assertEquals(150, glideMillis(20))
        assertEquals(1_500, glideMillis(60_000))
    }
}
