package dev.mockarr.app.ui.screens

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
}
