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
    fun `nudge distance scales with tick duration`() {
        val base = nudgeMeters(deflection = 1f, zoom = 17.0, latitudeDegrees = 45.0, dtSeconds = 0.2)
        val halfTick = nudgeMeters(deflection = 1f, zoom = 17.0, latitudeDegrees = 45.0, dtSeconds = 0.1)
        assertEquals(base / 2, halfTick, 1e-6)
    }

    @Test
    fun `response curve is dead near rest, fine in the middle, full at the rim`() {
        assertEquals(0f, responseCurve(0f))
        assertEquals(0f, responseCurve(0.08f))
        val half = responseCurve(0.5f)
        assertTrue(half in 0.18f..0.26f, "half stick is a fine range, was $half")
        assertEquals(1f, responseCurve(1f), 1e-6f)
        // Monotonic: more travel never means less speed.
        var last = -1f
        for (step in 0..20) {
            val next = responseCurve(step / 20f)
            assertTrue(next >= last)
            last = next
        }
    }

    @Test
    fun `nudge speed clamps at both ends and rests in the dead zone`() {
        // Inside the dead zone the stick does nothing at all — no creep.
        assertEquals(0.0, nudgeMeters(deflection = 0.05f, zoom = 22.0, latitudeDegrees = 0.0, dtSeconds = 1.0))
        // Deep zoom + slight deflection → tiny speed → clamped up to the minimum.
        val creep = nudgeMeters(deflection = 0.12f, zoom = 22.0, latitudeDegrees = 0.0, dtSeconds = 1.0)
        assertEquals(0.1, creep, 1e-9)
        // Whole-world zoom → clamped down to the maximum speed.
        val sprint = nudgeMeters(deflection = 1f, zoom = 0.0, latitudeDegrees = 0.0, dtSeconds = 1.0)
        assertEquals(10_000.0, sprint, 1e-9)
    }

    @Test
    fun `typical outdoor zooms are never speed-clamped`() {
        // The 8a report: zooming out stopped changing the stick's ground speed.
        // At city/regional zooms the doubling law must hold un-clamped.
        val z13 = nudgeMeters(deflection = 1f, zoom = 13.0, latitudeDegrees = 48.0, dtSeconds = 0.2)
        val z12 = nudgeMeters(deflection = 1f, zoom = 12.0, latitudeDegrees = 48.0, dtSeconds = 0.2)
        assertEquals(z13 * 2, z12, 1e-6)
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
