package dev.mockarr.app.ui

import dev.mockarr.core.mocklocation.MockStartResult
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Main-thread only (SimpleDateFormat is not thread-safe; all callers are composables).
private val routeTimestampFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

fun formatRouteTimestamp(epochMillis: Long): String = routeTimestampFormat.format(Date(epochMillis))

fun routeSummaryText(distanceMeters: Double, durationSeconds: Double): String {
    val km = distanceMeters / 1000.0
    val minutes = (durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
    return "%.1f km · about %d min".format(km, minutes)
}

fun Route.summaryText(): String = routeSummaryText(distanceMeters, durationSeconds)

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
