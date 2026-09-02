package dev.mockarr.app.ui.map

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchPinHitTest {

    private val density = 2f
    private val pin = Offset(200f, 400f) // the tip: the geocoded point on screen

    @Test
    fun `hits the drawn teardrop above the point`() {
        val height = searchPinHeightPx(density)

        assertTrue(hitSearchPin(Offset(200f, 400f - height / 2f), pin, density))
        assertTrue(hitSearchPin(Offset(200f, 400f - height + 1f), pin, density))
    }

    @Test
    fun `target is at least the 48dp touch size wide and forgives a thumb under the tip`() {
        assertTrue(hitSearchPin(Offset(200f + 47f, 380f), pin, density))
        assertFalse(hitSearchPin(Offset(200f + 49f, 380f), pin, density))
        assertTrue(hitSearchPin(Offset(200f, 400f + 15f), pin, density))
        assertFalse(hitSearchPin(Offset(200f, 400f + 17f), pin, density))
    }

    @Test
    fun `misses above the head`() {
        assertFalse(hitSearchPin(Offset(200f, 400f - searchPinHeightPx(density) - 2f), pin, density))
    }
}
