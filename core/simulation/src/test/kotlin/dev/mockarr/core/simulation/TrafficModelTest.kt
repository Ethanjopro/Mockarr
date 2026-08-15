package dev.mockarr.core.simulation

import java.time.DayOfWeek
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrafficModelTest {

    @Test
    fun `weekday rush hours peak at one point five`() {
        assertEquals(1.50, TrafficModel.congestionFactor(DayOfWeek.MONDAY, 8, 0), 0.001)
        assertEquals(1.50, TrafficModel.congestionFactor(DayOfWeek.MONDAY, 17, 0), 0.001)
    }

    @Test
    fun `peak exceeds shoulder exceeds night, weekend stays light`() {
        val peak = TrafficModel.congestionFactor(DayOfWeek.WEDNESDAY, 8, 0)
        val shoulder = TrafficModel.congestionFactor(DayOfWeek.WEDNESDAY, 10, 0)
        val night = TrafficModel.congestionFactor(DayOfWeek.WEDNESDAY, 2, 0)
        val weekendMidday = TrafficModel.congestionFactor(DayOfWeek.SATURDAY, 12, 0)
        assertTrue(peak > shoulder && shoulder > night, "$peak / $shoulder / $night")
        assertTrue(weekendMidday < peak, "weekend $weekendMidday vs peak $peak")
    }

    @Test
    fun `factor never drops below one`() {
        for (day in DayOfWeek.entries) {
            for (hour in 0..23) {
                for (minute in listOf(0, 15, 30, 45)) {
                    val factor = TrafficModel.congestionFactor(day, hour, minute)
                    assertTrue(factor >= 1.0, "$day $hour:$minute -> $factor")
                }
            }
        }
    }

    @Test
    fun `curve is continuous across hours, midnight, and week boundaries`() {
        for (day in DayOfWeek.entries) {
            for (hour in 0..22) {
                val before = TrafficModel.congestionFactor(day, hour, 59)
                val after = TrafficModel.congestionFactor(day, hour + 1, 0)
                assertTrue(abs(before - after) < 0.01, "$day $hour:59 -> ${hour + 1}:00")
            }
        }
        val friNight = TrafficModel.congestionFactor(DayOfWeek.FRIDAY, 23, 59)
        val satMorning = TrafficModel.congestionFactor(DayOfWeek.SATURDAY, 0, 0)
        assertTrue(abs(friNight - satMorning) < 0.01, "weekday->weekend wrap")
        val sunNight = TrafficModel.congestionFactor(DayOfWeek.SUNDAY, 23, 59)
        val monMorning = TrafficModel.congestionFactor(DayOfWeek.MONDAY, 0, 0)
        assertTrue(abs(sunNight - monMorning) < 0.01, "weekend->weekday wrap")
    }

    @Test
    fun `minutes interpolate linearly between hour anchors`() {
        // Monday 07:00 = 1.40, 08:00 = 1.50 -> 07:30 = 1.45
        assertEquals(1.45, TrafficModel.congestionFactor(DayOfWeek.MONDAY, 7, 30), 0.001)
    }
}
