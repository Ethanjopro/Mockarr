package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElevationSamplingTest {

    // ~111 m per 0.001° of longitude at the equator — evenly spaced.
    private fun points(count: Int): List<LatLng> = List(count) { LatLng(0.0, it * 0.001) }

    @Test
    fun `small routes sample every vertex`() {
        val indices = ElevationSampling.sampleIndices(points(20), maxSamples = 100)
        assertEquals((0..19).toList(), indices)
    }

    @Test
    fun `large routes cap samples and keep both endpoints`() {
        val indices = ElevationSampling.sampleIndices(points(250), maxSamples = 100)
        assertTrue(indices.size <= 100, "sampled ${indices.size}")
        assertEquals(0, indices.first())
        assertEquals(249, indices.last())
        assertTrue(indices.zipWithNext().all { (a, b) -> b > a }, "not strictly ascending")
    }

    @Test
    fun `interpolation is exact at samples and linear between`() {
        val routePoints = points(5) // indices 0..4, uniform spacing
        val altitudes = ElevationSampling.interpolate(
            points = routePoints,
            sampledIndices = listOf(0, 4),
            sampledElevations = listOf(10.0, 50.0),
        )
        assertEquals(5, altitudes.size)
        assertEquals(10.0, altitudes[0], 0.001)
        assertEquals(50.0, altitudes[4], 0.001)
        assertEquals(30.0, altitudes[2], 0.5) // midpoint
        assertEquals(20.0, altitudes[1], 0.5)
    }
}
