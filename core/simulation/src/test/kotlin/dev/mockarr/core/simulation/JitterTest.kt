package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JitterTest {

    private val origin = LatLng(37.0, -122.0)

    @Test
    fun `mean displacement is the half-normal mean of sigma`() {
        val jitter = Jitter(Random(7))
        val sigma = 3.0
        val samples = 4000
        val mean = (1..samples).sumOf { GeoMath.distanceMeters(origin, jitter.offset(origin, sigma)) } / samples
        val expected = sigma * sqrt(2.0 / PI)
        assertEquals(expected, mean, expected * 0.1)
    }

    @Test
    fun `directions cover the compass`() {
        val jitter = Jitter(Random(11))
        val quadrants = (1..400)
            .map { GeoMath.bearingDegrees(origin, jitter.offset(origin, 5.0)) }
            .map { (it / 90.0).toInt().coerceIn(0, 3) }
            .toSet()
        assertEquals(setOf(0, 1, 2, 3), quadrants)
    }

    @Test
    fun `zero sigma leaves the position untouched`() {
        val jitter = Jitter(Random(3))
        repeat(10) { assertEquals(origin, jitter.offset(origin, 0.0)) }
    }

    @Test
    fun `same seed gives the same sequence`() {
        val a = Jitter(Random(42))
        val b = Jitter(Random(42))
        repeat(20) { assertEquals(a.offset(origin, 4.0), b.offset(origin, 4.0)) }
        assertTrue(a.gaussian() == b.gaussian())
    }
}
