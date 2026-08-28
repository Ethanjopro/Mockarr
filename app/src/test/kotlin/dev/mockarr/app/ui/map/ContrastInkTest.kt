package dev.mockarr.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class ContrastInkTest {

    private val white = 0xFFFFFFFF.toInt()
    private val ink = 0xFF121319.toInt()
    private val indigo = 0xFF3949AB.toInt()
    private val paleIndigo = 0xFFDEE0FF.toInt()

    @Test
    fun `light text stays on a dark disc`() {
        assertEquals(white, contrastInk(fill = indigo, text = white, ring = ink))
    }

    @Test
    fun `light text flips to the ring colour on a pale disc`() {
        // Dark theme: the selection tint is near-white, so the ground ink wins.
        assertEquals(ink, contrastInk(fill = paleIndigo, text = white, ring = ink))
    }

    @Test
    fun `dark text flips on a dark disc`() {
        assertEquals(white, contrastInk(fill = indigo, text = ink, ring = white))
    }
}
