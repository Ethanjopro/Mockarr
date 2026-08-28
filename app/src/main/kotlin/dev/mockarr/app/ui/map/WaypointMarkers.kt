package dev.mockarr.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import dev.mockarr.app.ui.theme.MapPalette
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
private const val SELECTION_RING_GAP_DP = 1.5f
private const val SELECTION_RING_WIDTH_DP = 2.5f
private const val RANK_SELECTED_BOOST = 1_000
private const val RADIX_HEX = 16

/** Everything a marker/chip bitmap needs besides its own content. */
data class MarkerStyle(val density: Float, val palette: MapPalette)

/** Index (SORT_KEY) of the topmost waypoint marker under the tap, or null. */
internal fun MapLibreMap.waypointIndexAt(point: MapLibreLatLng, density: Float): Int? {
    val screen = projection.toScreenLocation(point)
    val radius = WAYPOINT_TAP_RADIUS_DP * density
    val rect = RectF(screen.x - radius, screen.y - radius, screen.x + radius, screen.y + radius)
    return queryRenderedFeatures(rect, WAYPOINT_LAYER)
        .mapNotNull { feature -> runCatching { feature.getNumberProperty(SORT_KEY).toInt() }.getOrNull() }
        .maxOrNull() // higher sort key renders on top — matches what the user sees
}

internal fun updateWaypoints(
    style: Style,
    waypoints: List<Waypoint>,
    selectedIndex: Int?,
    markerStyle: MarkerStyle,
) {
    // The palette is part of the image name: addImage caches by name and a
    // theme switch must not keep serving the other theme's bitmap.
    val paletteTag = markerStyle.palette.hashCode().toUInt().toString(RADIX_HEX)
    val features = waypoints.mapIndexed { index, waypoint ->
        val role = when (index) {
            0 -> "start"
            waypoints.lastIndex -> "end"
            else -> "via"
        }
        val hasWait = waypoint.waitSeconds > 0
        val selected = index == selectedIndex
        // Wait and selection flags are part of the key: addImage caches by
        // name, so a same-named icon would keep showing the stale bitmap.
        val icon = "waypoint-$role-${index + 1}" +
            (if (hasWait) "-wait" else "") +
            (if (selected) "-sel" else "") +
            "-$paletteTag"
        style.addImage(icon, waypointBitmap(role, index + 1, hasWait, selected, markerStyle))
        // RANK_KEY drives draw order (selected wins the overlap); SORT_KEY
        // stays the untouched tap identity read back by waypointIndexAt.
        val rank = if (selected) index + RANK_SELECTED_BOOST else index
        Feature.fromGeometry(waypoint.position.toPoint()).apply {
            addStringProperty(ICON_KEY, icon)
            addNumberProperty(SORT_KEY, index)
            addNumberProperty(RANK_KEY, rank)
        }
    }
    style.getSourceAs<GeoJsonSource>(WAYPOINT_SOURCE)
        ?.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun waypointBitmap(
    role: String,
    number: Int,
    hasWait: Boolean,
    selected: Boolean,
    markerStyle: MarkerStyle,
): Bitmap {
    val (density, palette) = markerStyle
    val fillRadius = WAYPOINT_RADIUS_DP * density
    val stroke = WAYPOINT_STROKE_DP * density
    val ringGap = SELECTION_RING_GAP_DP * density
    val ringWidth = SELECTION_RING_WIDTH_DP * density
    // Every variant reserves ring headroom: uniform bitmap size keeps the
    // icon's center anchor fixed, so selecting never shifts the marker.
    val size = ceil((fillRadius + stroke + ringGap + ringWidth) * 2).toInt()
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = when (role) {
        "start" -> palette.stopStart
        "end" -> palette.stopEnd
        else -> palette.stopVia
    }
    canvas.drawCircle(center, center, fillRadius, paint)
    paint.color = palette.stopRing
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = stroke
    canvas.drawCircle(center, center, fillRadius + stroke / 2f, paint)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (role == "end") palette.stopRing else palette.stopText
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
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.waitBadge }
        canvas.drawCircle(center + offset, center - offset, badgeRadius, badge)
        badge.color = palette.stopRing
        badge.style = Paint.Style.STROKE
        badge.strokeWidth = stroke / 2f
        canvas.drawCircle(center + offset, center - offset, badgeRadius, badge)
    }
    if (selected) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.selection
            style = Paint.Style.STROKE
            strokeWidth = ringWidth
        }
        canvas.drawCircle(center, center, fillRadius + stroke + ringGap + ringWidth / 2f, ring)
    }
    return bitmap
}
