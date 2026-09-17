package dev.mockarr.app.ui.screens

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RollTrackerTest {

    @Test
    fun `first value rolls up`() {
        assertTrue(RollTracker().advance("2 min", 120.0))
    }

    @Test
    fun `a shrinking number rolls down and a growing one rolls up`() {
        val tracker = RollTracker()
        tracker.advance("2 min", 120.0)

        assertFalse(tracker.advance("1 min", 60.0))
        assertTrue(tracker.advance("3 min", 180.0))
    }

    @Test
    fun `direction survives a change of format`() {
        val tracker = RollTracker()
        tracker.advance("2 min", 120.0)

        assertFalse(tracker.advance("59 s", 59.0))
    }

    @Test
    fun `an unchanged magnitude keeps the last direction`() {
        val tracker = RollTracker()
        tracker.advance("2 min", 120.0)
        tracker.advance("1 min", 60.0)

        assertFalse(tracker.advance("1 min", 60.0))
    }

    @Test
    fun `no magnitude means no basis to roll down`() {
        val tracker = RollTracker()
        tracker.advance("—", null)

        assertTrue(tracker.advance("5 mph", null))
    }
}
