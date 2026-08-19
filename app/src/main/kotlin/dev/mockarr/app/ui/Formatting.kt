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

fun routeSummaryText(
    distanceMeters: Double,
    durationSeconds: Double,
    units: DistanceUnits,
    trafficFactor: Double = 1.0,
    extraSeconds: Double = 0.0,
): String {
    val minutes = ((durationSeconds * trafficFactor + extraSeconds) / 60.0).roundToInt().coerceAtLeast(1)
    val suffix = if (trafficFactor >= TRAFFIC_SUFFIX_THRESHOLD) " (traffic)" else ""
    val duration = formatDurationShort(minutes * SECONDS_PER_MINUTE.toDouble())
    return "${formatDistance(distanceMeters, units)} · about $duration$suffix"
}

fun Route.summaryText(units: DistanceUnits, trafficFactor: Double = 1.0): String =
    routeSummaryText(
        distanceMeters,
        durationSeconds,
        units,
        trafficFactor,
        waypointWaitsSeconds.sum().toDouble(),
    )

private const val TRAFFIC_SUFFIX_THRESHOLD = 1.05

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/** "45 s" / "3 min" / "1 h" / "1 h 15 min" — rounded up, never "0 min". */
fun formatDurationShort(seconds: Double): String {
    val total = ceil(seconds).toInt().coerceAtLeast(0)
    val totalMinutes = ceil(total / 60.0).toInt()
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return when {
        total < SECONDS_PER_MINUTE -> "$total s"
        hours == 0 -> "$totalMinutes min"
        minutes == 0 -> "$hours h"
        else -> "$hours h $minutes min"
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
