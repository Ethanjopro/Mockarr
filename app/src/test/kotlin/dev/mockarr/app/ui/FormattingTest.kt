package dev.mockarr.app.ui

import dev.mockarr.core.model.DistanceUnits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormattingTest {

    private fun summary(durationSeconds: Double, trafficFactor: Double = 1.0): String =
        routeSummaryText(1000.0, durationSeconds, DistanceUnits.KILOMETERS, trafficFactor)

    @Test
    fun `summary under an hour stays in minutes`() {
        assertTrue(summary(45.0 * 60).endsWith("about 45 min"))
    }

    @Test
    fun `summary at exactly one hour reads 1 h`() {
        assertTrue(summary(60.0 * 60).endsWith("about 1 h"))
    }

    @Test
    fun `summary over an hour reads hours and minutes`() {
        assertTrue(summary(75.0 * 60).endsWith("about 1 h 15 min"))
    }

    @Test
    fun `summary keeps the traffic suffix after hour formatting`() {
        assertTrue(summary(60.0 * 60, trafficFactor = 1.25).endsWith("about 1 h 15 min (traffic)"))
    }

    @Test
    fun `summary includes extra wait seconds`() {
        val text = routeSummaryText(
            1000.0,
            50.0 * 60,
            DistanceUnits.KILOMETERS,
            extraSeconds = 10.0 * 60,
        )
        assertTrue(text.endsWith("about 1 h"))
    }

    @Test
    fun `duration under a minute is seconds`() {
        assertEquals("45 s", formatDurationShort(45.0))
    }

    @Test
    fun `duration under an hour is minutes`() {
        assertEquals("5 min", formatDurationShort(300.0))
    }

    @Test
    fun `duration over an hour reads hours and minutes`() {
        assertEquals("1 h 15 min", formatDurationShort(4500.0))
    }

    @Test
    fun `duration just under two hours never reads 1 h 60 min`() {
        assertEquals("2 h", formatDurationShort(7199.0))
    }
}
