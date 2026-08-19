package dev.mockarr.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import dev.mockarr.core.model.Waypoint
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import kotlin.math.ceil
import org.maplibre.android.geometry.LatLng as MapLibreLatLng

private const val WAYPOINT_TAP_RADIUS_DP = 16f
private const val WAYPOINT_RADIUS_DP = 11f
private const val WAYPOINT_STROKE_DP = 2.5f
private const val WAYPOINT_TEXT_DP = 13f
private const val WAIT_BADGE_RADIUS_DP = 4.5f
private const val WAIT_BADGE_OFFSET_FRACTION = 0.7f
private val WAYPOINT_START_COLOR = Color.rgb(46, 125, 50)
private val WAYPOINT_END_COLOR = Color.rgb(198, 40, 40)
private val WAYPOINT_VIA_COLOR = Color.rgb(69, 90, 100)
private val WAIT_BADGE_COLOR = Color.rgb(249, 168, 37)

/** Index (SORT_KEY) of the topmost waypoint marker under the tap, or null. */
internal fun MapLibreMap.waypointIndexAt(point: MapLibreLatLng, density: Float): Int? {
    val screen = projection.toScreenLocation(point)
    val radius = WAYPOINT_TAP_RADIUS_DP * density
    val rect = RectF(screen.x - radius, screen.y - radius, screen.x + radius, screen.y + radius)
    return queryRenderedFeatures(rect, WAYPOINT_LAYER)
        .mapNotNull { feature -> runCatching { feature.getNumberProperty(SORT_KEY).toInt() }.getOrNull() }
        .maxOrNull() // higher sort key renders on top — matches what the user sees
}

internal fun updateWaypoints(style: Style, waypoints: List<Waypoint>, density: Float) {
    val features = waypoints.mapIndexed { index, waypoint ->
        val role = when (index) {
            0 -> "start"
            waypoints.lastIndex -> "end"
            else -> "via"
        }
        val hasWait = waypoint.waitSeconds > 0
        // The wait flag is part of the key: addImage caches by name, so a
        // same-named icon would keep showing the stale badge-less bitmap.
        val icon = "waypoint-$role-${index + 1}" + if (hasWait) "-wait" else ""
        style.addImage(icon, waypointBitmap(role, index + 1, density, hasWait))
        Feature.fromGeometry(waypoint.position.toPoint()).apply {
            addStringProperty(ICON_KEY, icon)
            // Higher sort key renders on top — a later stop wins the overlap.
            addNumberProperty(SORT_KEY, index)
        }
    }
    style.getSourceAs<GeoJsonSource>(WAYPOINT_SOURCE)
        ?.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun waypointBitmap(role: String, number: Int, density: Float, hasWait: Boolean): Bitmap {
    val fillRadius = WAYPOINT_RADIUS_DP * density
    val stroke = WAYPOINT_STROKE_DP * density
    val size = ceil((fillRadius + stroke) * 2).toInt()
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = when (role) {
        "start" -> WAYPOINT_START_COLOR
        "end" -> WAYPOINT_END_COLOR
        else -> WAYPOINT_VIA_COLOR
    }
    canvas.drawCircle(center, center, fillRadius, paint)
    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = stroke
    canvas.drawCircle(center, center, fillRadius + stroke / 2f, paint)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textSize = WAYPOINT_TEXT_DP * density
        textAlign = Paint.Align.CENTER
    }
    val baseline = center - (text.ascent() + text.descent()) / 2f
    canvas.drawText(number.toString(), center, baseline, text)
    if (hasWait) {
        // Amber wait badge tucked inside the top-right of the marker circle,
        // so the bitmap size (and the icon's anchor point) stays unchanged.
        val badgeRadius = WAIT_BADGE_RADIUS_DP * density
        val offset = fillRadius * WAIT_BADGE_OFFSET_FRACTION
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WAIT_BADGE_COLOR }
        canvas.drawCircle(center + offset, center - offset, badgeRadius, badge)
        badge.color = Color.WHITE
        badge.style = Paint.Style.STROKE
        badge.strokeWidth = stroke / 2f
        canvas.drawCircle(center + offset, center - offset, badgeRadius, badge)
    }
    return bitmap
}
