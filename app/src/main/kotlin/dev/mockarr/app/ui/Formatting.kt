package dev.mockarr.app.ui

import dev.mockarr.core.mocklocation.MockStartResult
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val METERS_PER_KILOMETER = 1000.0
private const val METERS_PER_MILE = 1609.344

// Main-thread only (SimpleDateFormat is not thread-safe; all callers are composables).
private val routeTimestampFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

fun formatRouteTimestamp(epochMillis: Long): String = routeTimestampFormat.format(Date(epochMillis))

/** Meters converted into the unit's headline value (km or mi). */
fun DistanceUnits.fromMeters(meters: Double): Double = when (this) {
    DistanceUnits.KILOMETERS -> meters / METERS_PER_KILOMETER
    DistanceUnits.MILES -> meters / METERS_PER_MILE
}

fun DistanceUnits.abbreviation(): String = when (this) {
    DistanceUnits.KILOMETERS -> "km"
    DistanceUnits.MILES -> "mi"
}

fun formatDistance(meters: Double, units: DistanceUnits): String =
    "%.1f %s".format(units.fromMeters(meters), units.abbreviation())

/** "3.2 / 5.0 km" progress pair used by the playback card and notification. */
fun formatDistanceProgress(doneMeters: Double, totalMeters: Double, units: DistanceUnits): String =
    "%.1f / %.1f %s".format(
        units.fromMeters(doneMeters),
        units.fromMeters(totalMeters),
        units.abbreviation(),
    )

fun routeSummaryText(distanceMeters: Double, durationSeconds: Double, units: DistanceUnits): String {
    val minutes = (durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
    return "${formatDistance(distanceMeters, units)} · about $minutes min"
}

fun Route.summaryText(units: DistanceUnits): String =
    routeSummaryText(distanceMeters, durationSeconds, units)

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

/** "45 s left" / "3 min left" / "1 h 12 min left" — rounded up, never "0 min". */
fun formatTimeRemaining(seconds: Double): String {
    val total = ceil(seconds).toInt().coerceAtLeast(0)
    return when {
        total < SECONDS_PER_MINUTE -> "$total s left"
        total < SECONDS_PER_HOUR -> "${ceil(total / 60.0).toInt()} min left"
        else -> {
            val hours = total / SECONDS_PER_HOUR
            val minutes = ceil((total % SECONDS_PER_HOUR) / 60.0).toInt()
            "$hours h $minutes min left"
        }
    }
}

fun RoutingProfile.label(): String = when (this) {
    RoutingProfile.DRIVING -> "Driving"
    RoutingProfile.WALKING -> "Walking"
    RoutingProfile.CYCLING -> "Cycling"
}

/** User-facing failure copy for a mock session start, or null on success. */
fun MockStartResult.errorMessageOrNull(): String? = when (this) {
    MockStartResult.Ok -> null
    MockStartResult.NotSelectedAsMockApp ->
        "Mockarr isn't selected as the mock location app — open the Setup checklist."
    is MockStartResult.ProviderError -> message
}
