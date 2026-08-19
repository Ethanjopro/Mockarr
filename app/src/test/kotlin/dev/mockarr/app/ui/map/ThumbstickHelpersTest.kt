package dev.mockarr.app.ui.map

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThumbstickHelpersTest {

    @Test
    fun `stick bearing maps screen quadrants to compass`() {
        assertEquals(0.0, stickBearingDegrees(0f, -1f), 1e-9)
        assertEquals(90.0, stickBearingDegrees(1f, 0f), 1e-9)
        assertEquals(180.0, stickBearingDegrees(0f, 1f), 1e-9)
        assertEquals(270.0, stickBearingDegrees(-1f, 0f), 1e-9)
    }

    @Test
    fun `zooming out one level doubles the nudge distance`() {
        // z17/z18 sit inside the speed clamps at the equator.
        val near = nudgeMeters(deflection = 1f, zoom = 18.0, latitudeDegrees = 0.0, dtSeconds = 0.2)
        val far = nudgeMeters(deflection = 1f, zoom = 17.0, latitudeDegrees = 0.0, dtSeconds = 0.2)
        assertEquals(near * 2, far, 1e-6)
    }

    @Test
    fun `nudge distance scales with tick duration and deflection`() {
        val base = nudgeMeters(deflection = 1f, zoom = 17.0, latitudeDegrees = 45.0, dtSeconds = 0.2)
        val halfTick = nudgeMeters(deflection = 1f, zoom = 17.0, latitudeDegrees = 45.0, dtSeconds = 0.1)
        val halfStick = nudgeMeters(deflection = 0.5f, zoom = 17.0, latitudeDegrees = 45.0, dtSeconds = 0.2)
        assertEquals(base / 2, halfTick, 1e-6)
        assertEquals(base / 2, halfStick, 1e-6)
    }

    @Test
    fun `nudge speed clamps at both ends`() {
        // Deep zoom + slight deflection → tiny speed → clamped up to the minimum.
        val creep = nudgeMeters(deflection = 0.01f, zoom = 22.0, latitudeDegrees = 0.0, dtSeconds = 1.0)
        assertEquals(0.3, creep, 1e-9)
        // Whole-world zoom → clamped down to the maximum speed.
        val sprint = nudgeMeters(deflection = 1f, zoom = 0.0, latitudeDegrees = 0.0, dtSeconds = 1.0)
        assertEquals(1_000.0, sprint, 1e-9)
    }

    @Test
    fun `clamp keeps short drags and shortens long ones`() {
        val short = Offset(3f, 4f)
        assertEquals(short, clampToRadius(short, maxRadius = 10f))
        val clamped = clampToRadius(Offset(30f, 40f), maxRadius = 10f)
        assertEquals(10f, clamped.getDistance(), 1e-4f)
        assertTrue(clamped.x / clamped.y == 30f / 40f, "direction preserved")
    }
}
