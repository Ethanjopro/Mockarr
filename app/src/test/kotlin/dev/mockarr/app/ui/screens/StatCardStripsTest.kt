package dev.mockarr.app.ui.screens

import dev.mockarr.app.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatCardStripsTest {

    private val ready = StripModel("Ready to drive", StripTone.Ready)

    @Test
    fun `same text as the primary is hidden`() {
        assertNull(visibleSecondary(ready, ready.copy()))
    }

    @Test
    fun `different text passes through`() {
        val holding = StripModel("Holding at pin", StripTone.Hold)
        assertEquals(ready, visibleSecondary(holding, ready))
    }

    @Test
    fun `null stays null`() {
        assertNull(visibleSecondary(ready, null))
    }

    @Test
    fun `hold over a builder route reads ready to drive`() {
        assertEquals(R.string.strip_ready, res())
    }

    @Test
    fun `hold over a loaded route outside the builder reads route ready`() {
        assertEquals(R.string.strip_route_loaded, res(builder = false))
    }

    @Test
    fun `no second band without a hold, while driving, while moving a stop, or without a route`() {
        assertNull(res(holding = false))
        assertNull(res(playing = true))
        assertNull(res(moving = true))
        assertNull(res(hasRoute = false))
    }

    private fun res(
        holding: Boolean = true,
        playing: Boolean = false,
        moving: Boolean = false,
        hasRoute: Boolean = true,
        builder: Boolean = true,
    ): Int? = secondaryStripRes(holding, playing, moving, hasRoute, builder)
}
