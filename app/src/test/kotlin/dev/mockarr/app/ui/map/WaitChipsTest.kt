package dev.mockarr.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class WaitChipsTest {

    @Test
    fun `chip countdown under an hour is m colon ss`() {
        assertEquals("4:32", formatChipCountdown(272))
        assertEquals("0:45", formatChipCountdown(45))
    }

    @Test
    fun `chip countdown over an hour includes hours`() {
        assertEquals("1:02:05", formatChipCountdown(3725))
    }

    @Test
    fun `chip countdown clamps negatives to zero`() {
        assertEquals("0:00", formatChipCountdown(-3))
    }
}
