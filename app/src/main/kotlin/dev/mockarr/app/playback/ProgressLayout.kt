package dev.mockarr.app.playback

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import dev.mockarr.app.R
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile

/**
 * The drive as the notification's progress bar shows it (Android 16 Live
 * Updates): whole-metre leg lengths are the bar's segments, the stops between
 * them its points.
 */
internal class ProgressLayout(val segmentMeters: List<Int>, val stopMeters: List<Int>, val profile: RoutingProfile) {
    val totalMeters: Int = segmentMeters.sum()
}

/**
 * One segment per leg (at least 1 m so a zero-length leg still draws) and one
 * point per intermediate stop at its cumulative distance. Null when the route
 * carries no legs, in which case the bar stays plain.
 */
internal fun progressLayoutOf(route: Route, profile: RoutingProfile): ProgressLayout? {
    if (route.legs.isEmpty()) return null
    val segments = route.legs.map { leg -> leg.segmentDistancesMeters.sum().toInt().coerceAtLeast(1) }
    val stops = segments.runningReduce(Int::plus).dropLast(1)
    return ProgressLayout(segments, stops, profile)
}

/** The style for [layout] at [progress] (0–1): tracker icon by travel mode, a flag at the end. */
internal fun Context.progressStyle(layout: ProgressLayout, progress: Double): NotificationCompat.ProgressStyle =
    NotificationCompat.ProgressStyle()
        .setProgressSegments(layout.segmentMeters.map { NotificationCompat.ProgressStyle.Segment(it) })
        .setProgressPoints(layout.stopMeters.map { NotificationCompat.ProgressStyle.Point(it) })
        .setProgress((progress * layout.totalMeters).toInt().coerceIn(0, layout.totalMeters))
        .setStyledByProgress(true)
        .setProgressTrackerIcon(IconCompat.createWithResource(this, layout.profile.trackerIconRes()))
        .setProgressEndIcon(IconCompat.createWithResource(this, R.drawable.ic_flag))

private fun RoutingProfile.trackerIconRes(): Int = when (this) {
    RoutingProfile.DRIVING -> R.drawable.ic_car
    RoutingProfile.WALKING -> R.drawable.ic_walk
    RoutingProfile.CYCLING -> R.drawable.ic_bike
}
