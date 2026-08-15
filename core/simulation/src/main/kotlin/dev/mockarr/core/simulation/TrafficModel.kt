package dev.mockarr.core.simulation

import java.time.DayOfWeek

/**
 * Deterministic rush-hour heuristic: no free routing service has real traffic
 * data, so ETAs get a time-of-day congestion factor instead. Factors are
 * anchored per hour and lerped by minute, so estimates change smoothly.
 * Callers inject the clock — this object never reads time itself.
 */
object TrafficModel {

    // Factor at HH:00; linear interpolation to the next hour (wrapping 23 -> 0).
    // Both tables are 1.00 at 23:00 and 00:00, so midnight and
    // weekday <-> weekend transitions are continuous.
    private val WEEKDAY = doubleArrayOf(
        1.00, 1.00, 1.00, 1.00, 1.00, 1.05, // 00-05
        1.20, 1.40, 1.50, 1.40, 1.20, 1.15, // 06-11
        1.20, 1.20, 1.15, 1.25, 1.40, 1.50, // 12-17
        1.45, 1.30, 1.15, 1.10, 1.05, 1.00, // 18-23
    )
    private val WEEKEND = doubleArrayOf(
        1.00, 1.00, 1.00, 1.00, 1.00, 1.00, // 00-05
        1.00, 1.00, 1.05, 1.05, 1.10, 1.10, // 06-11
        1.10, 1.10, 1.10, 1.10, 1.10, 1.05, // 12-17
        1.05, 1.05, 1.00, 1.00, 1.00, 1.00, // 18-23
    )

    /** Duration multiplier ≥ 1.0 for the given local time. */
    fun congestionFactor(dayOfWeek: DayOfWeek, hour: Int, minute: Int): Double {
        require(hour in 0..23 && minute in 0..59) { "Invalid time $hour:$minute" }
        val table = if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            WEEKEND
        } else {
            WEEKDAY
        }
        val from = table[hour]
        val to = table[(hour + 1) % HOURS_PER_DAY]
        return from + (to - from) * (minute / MINUTES_PER_HOUR)
    }

    private const val HOURS_PER_DAY = 24
    private const val MINUTES_PER_HOUR = 60.0
}
