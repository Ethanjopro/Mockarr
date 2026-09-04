package dev.mockarr.app.ui

import dev.mockarr.core.model.DistanceUnits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

    @Test
    fun `short distances read in whole feet or metres, rounded to ten`() {
        // The 80 m building route that showed "0.0 mi".
        assertEquals(DistanceParts.Small(260), distanceParts(80.0, DistanceUnits.MILES))
        assertEquals(DistanceParts.Small(80), distanceParts(80.0, DistanceUnits.KILOMETERS))
        assertEquals(DistanceParts.Small(150), distanceParts(45.0, DistanceUnits.MILES))
        assertEquals(DistanceParts.Small(50), distanceParts(45.0, DistanceUnits.KILOMETERS))
        assertEquals(DistanceParts.Small(0), distanceParts(0.0, DistanceUnits.MILES))
    }

    @Test
    fun `a tenth of the headline unit switches back to the decimal form`() {
        assertEquals(DistanceParts.Small(520), distanceParts(160.0, DistanceUnits.MILES))
        assertIs<DistanceParts.Large>(distanceParts(161.0, DistanceUnits.MILES))
        assertEquals(DistanceParts.Small(90), distanceParts(94.0, DistanceUnits.KILOMETERS))
        assertEquals(DistanceParts.Large(0.1), distanceParts(100.0, DistanceUnits.KILOMETERS))
        assertEquals(DistanceParts.Large(5.0), distanceParts(5000.0, DistanceUnits.KILOMETERS))
    }
}
