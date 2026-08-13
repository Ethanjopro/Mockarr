package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals

class Polyline6Test {

    @Test
    fun `round-trips a realistic path`() {
        val original = listOf(
            LatLng(48.858400, 2.294500),
            LatLng(48.858512, 2.295130),
            LatLng(48.859201, 2.296800),
            LatLng(48.857900, 2.301200),
            LatLng(-33.856784, 151.215297),
        )
        val decoded = Polyline6.decode(Polyline6.encode(original))
        assertEquals(original.size, decoded.size)
        original.zip(decoded).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-6)
            assertEquals(a.longitude, b.longitude, 1e-6)
        }
    }

    @Test
    fun `decodes a single tiny delta`() {
        // lat delta of 1e-6 encodes to 'A' (1 -> zigzag 2 -> chr(2+63)), lng 0 -> '?'
        val decoded = Polyline6.decode("A?")
        assertEquals(1, decoded.size)
        assertEquals(0.000001, decoded.first().latitude, 1e-9)
        assertEquals(0.0, decoded.first().longitude, 1e-9)
    }

    @Test
    fun `handles negative coordinates`() {
        val original = listOf(LatLng(-0.000001, -0.000001), LatLng(-1.5, -2.75))
        val decoded = Polyline6.decode(Polyline6.encode(original))
        assertEquals(-1.5, decoded[1].latitude, 1e-6)
        assertEquals(-2.75, decoded[1].longitude, 1e-6)
    }

    @Test
    fun `empty input decodes to empty list`() {
        assertEquals(emptyList(), Polyline6.decode(""))
    }
}
