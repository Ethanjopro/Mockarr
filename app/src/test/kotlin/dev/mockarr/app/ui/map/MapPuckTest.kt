package dev.mockarr.app.ui.map

import kotlin.math.cos
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapPuckTest {

    /** MapLibre's `interpolate(exponential(2), zoom, …)` evaluated at [zoom] over [stops]. */
    private fun radiusAt(stops: List<Pair<Float, Float>>, zoom: Double): Double {
        if (zoom <= stops.first().first) return stops.first().second.toDouble()
        val upper = stops.indexOfFirst { it.first >= zoom }.takeIf { it > 0 } ?: return stops.last().second.toDouble()
        val (z0, v0) = stops[upper - 1]
        val (z1, v1) = stops[upper]
        val t = (2.0.pow(zoom - z0) - 1) / (2.0.pow((z1 - z0).toDouble()) - 1)
        return v0 + t * (v1 - v0)
    }

    /** The metre-true radius in dp at [zoom] and [latitude] (512-dp world at z0). */
    private fun metreTrue(radiusMeters: Double, latitude: Double, zoom: Double) =
        radiusMeters / (78_271.517 * cos(Math.toRadians(latitude)) / 2.0.pow(zoom))

    @Test
    fun `wobble radius is twice sigma, and absent when the wobble is off or zero`() {
        assertEquals(3.0, wobbleRadiusOf(enabled = true, sigmaMeters = 1.5))
        assertNull(wobbleRadiusOf(enabled = false, sigmaMeters = 1.5))
        assertNull(wobbleRadiusOf(enabled = true, sigmaMeters = 0.0))
    }

    @Test
    fun `the circle holds the floor while the true radius is smaller`() {
        // 3 m at Paris is ~4 dp at z16: the dot would cover it, so the floor shows instead.
        val stops = wobbleRadiusStops(radiusMeters = 3.0, latitude = 48.86)
        assertEquals(RANGE_FLOOR_DP.toDouble(), radiusAt(stops, 15.0), 1e-3)
        assertEquals(RANGE_FLOOR_DP.toDouble(), radiusAt(stops, 16.0), 1e-3)
    }

    @Test
    fun `the circle is metre-true once it outgrows the floor`() {
        val stops = wobbleRadiusStops(radiusMeters = 20.0, latitude = 48.86)
        for (zoom in listOf(17.0, 18.0, 19.0)) {
            val expected = metreTrue(20.0, 48.86, zoom)
            assertEquals(expected, radiusAt(stops, zoom), expected * 1e-4)
        }
    }

    @Test
    fun `the same metres draw larger nearer the poles`() {
        val equator = radiusAt(wobbleRadiusStops(20.0, latitude = 0.0), 19.0)
        val north = radiusAt(wobbleRadiusStops(20.0, latitude = 60.0), 19.0)
        assertEquals(2.0, north / equator, 1e-3)
    }

    @Test
    fun `a tiny radius never leaves the floor`() {
        assertEquals(listOf(0f to RANGE_FLOOR_DP, 24f to RANGE_FLOOR_DP), wobbleRadiusStops(1e-9, latitude = 0.0))
    }

    @Test
    fun `a huge radius is metre-true from zoom 0`() {
        val stops = wobbleRadiusStops(radiusMeters = 2_000_000.0, latitude = 0.0)
        assertEquals(2, stops.size)
        assertTrue(stops.first().second > RANGE_FLOOR_DP)
        assertEquals(metreTrue(2_000_000.0, 0.0, 3.0), radiusAt(stops, 3.0), 1e-2)
    }

    @Test
    fun `glide follows the fix interval within its clamp`() {
        assertEquals(1_000, glideMillis(1_000))
        assertEquals(500, glideMillis(500))
        assertEquals(150, glideMillis(20))
        assertEquals(1_500, glideMillis(60_000))
    }
}
