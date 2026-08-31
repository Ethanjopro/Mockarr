package dev.mockarr.app.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.mockarr.app.R
import dev.mockarr.core.model.DistanceUnits
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

private const val METERS_PER_KILOMETER = 1000.0
private const val METERS_PER_MILE = 1609.344
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

// Main-thread only (SimpleDateFormat is not thread-safe; all callers are composables).
private val routeTimestampFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

fun formatRouteTimestamp(epochMillis: Long): String = routeTimestampFormat.format(Date(epochMillis))

/** Meters converted into the unit's headline value (km or mi). */
fun DistanceUnits.fromMeters(meters: Double): Double = when (this) {
    DistanceUnits.KILOMETERS -> meters / METERS_PER_KILOMETER
    DistanceUnits.MILES -> meters / METERS_PER_MILE
}

/** A duration split the way the copy reads it: seconds under a minute, otherwise hours + minutes. */
data class DurationParts(val seconds: Int?, val hours: Int, val minutes: Int)

/** Rounded up, never "0 min": 45 s · 5 min · 1 h · 1 h 15 min; 7199 s is 2 h, not 1 h 60 min. */
fun durationParts(seconds: Double): DurationParts {
    val total = ceil(seconds).toInt().coerceAtLeast(0)
    if (total < SECONDS_PER_MINUTE) return DurationParts(seconds = total, hours = 0, minutes = 0)
    val totalMinutes = ceil(total / 60.0).toInt()
    return DurationParts(
        seconds = null,
        hours = totalMinutes / MINUTES_PER_HOUR,
        minutes = totalMinutes % MINUTES_PER_HOUR,
    )
}

/**
 * Every user-facing number, worded from resources: the unit names live in
 * `strings.xml` like all other copy, so the notification, the card and the
 * saved-route list read the same and translate together.
 */
class Formatter(private val res: Resources) {

    fun distance(meters: Double, units: DistanceUnits): String =
        res.getString(R.string.distance_value, units.fromMeters(meters), unitName(units))

    /** "3.2 / 5.0 km" progress pair used by the notification. */
    fun distanceProgress(doneMeters: Double, totalMeters: Double, units: DistanceUnits): String =
        res.getString(
            R.string.distance_progress,
            units.fromMeters(doneMeters),
            units.fromMeters(totalMeters),
            unitName(units),
        )

    /** "45 s" / "3 min" / "1 h" / "1 h 15 min". */
    fun duration(seconds: Double): String {
        val parts = durationParts(seconds)
        return when {
            parts.seconds != null -> res.getString(R.string.unit_seconds_short, parts.seconds)
            parts.hours == 0 -> res.getString(R.string.unit_minutes_short, parts.minutes)
            parts.minutes == 0 -> res.getString(R.string.unit_hours_short, parts.hours)
            else -> res.getString(R.string.unit_hours_minutes_short, parts.hours, parts.minutes)
        }
    }

    private fun unitName(units: DistanceUnits): String = when (units) {
        DistanceUnits.KILOMETERS -> res.getString(R.string.unit_km)
        DistanceUnits.MILES -> res.getString(R.string.unit_mi)
    }
}

/** The screen's formatter, bound to the current configuration's resources. */
@Composable
fun rememberFormatter(): Formatter {
    val resources = LocalContext.current.resources
    return remember(resources) { Formatter(resources) }
}
