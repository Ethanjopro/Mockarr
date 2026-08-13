package dev.mockarr.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class GeoMathTest {

    private val berlin = LatLng(52.5200, 13.4050)
    private val munich = LatLng(48.1374, 11.5755)

    @Test
    fun `distance between Berlin and Munich is about 504 km`() {
        val distance = GeoMath.distanceMeters(berlin, munich)
        assertEquals(504_000.0, distance, 2_000.0)
    }

    @Test
    fun `distance to the same point is zero`() {
        assertEquals(0.0, GeoMath.distanceMeters(berlin, berlin), 0.001)
    }

    @Test
    fun `bearing due east along the equator is 90 degrees`() {
        val bearing = GeoMath.bearingDegrees(LatLng(0.0, 0.0), LatLng(0.0, 1.0))
        assertEquals(90.0, bearing, 0.01)
    }

    @Test
    fun `destination round-trips with distance and bearing`() {
        val start = LatLng(52.5200, 13.4050)
        val bearing = 45.0
        val distance = 1_000.0
        val end = GeoMath.destination(start, bearing, distance)
        assertEquals(distance, GeoMath.distanceMeters(start, end), 1.0)
        assertEquals(bearing, GeoMath.bearingDegrees(start, end), 0.1)
    }
}
