package dev.mockarr.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingTest {

    @Test
    fun `duration under a minute is seconds`() {
        assertEquals(DurationParts(seconds = 45, hours = 0, minutes = 0), durationParts(45.0))
    }

    @Test
    fun `duration under an hour is minutes`() {
        assertEquals(DurationParts(seconds = null, hours = 0, minutes = 5), durationParts(300.0))
    }

    @Test
    fun `duration over an hour reads hours and minutes`() {
        assertEquals(DurationParts(seconds = null, hours = 1, minutes = 15), durationParts(4500.0))
    }

    @Test
    fun `duration just under two hours never reads 1 h 60 min`() {
        assertEquals(DurationParts(seconds = null, hours = 2, minutes = 0), durationParts(7199.0))
    }

    @Test
    fun `fractional seconds round up`() {
        assertEquals(DurationParts(seconds = 46, hours = 0, minutes = 0), durationParts(45.2))
    }
}
