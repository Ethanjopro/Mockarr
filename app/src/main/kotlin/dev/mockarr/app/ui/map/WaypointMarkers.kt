package dev.mockarr.app.ui.map

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import dev.mockarr.app.ui.theme.MapPalette
import dev.mockarr.core.model.Waypoint
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import kotlin.math.ceil
import org.maplibre.android.geometry.LatLng as MapLibreLatLng

internal const val WAYPOINT_RADIUS_DP = 11f
internal const val WAYPOINT_STROKE_DP = 2.5f
private const val WAYPOINT_TEXT_DP = 13f
internal const val SELECTED_GROW_DP = 2f
private const val LUMINANCE_THRESHOLD = 0.5
private const val LUMA_R = 0.299
private const val LUMA_G = 0.587
private const val LUMA_B = 0.114
private const val CHANNEL_MAX = 255.0
private const val CHANNEL_MASK = 0xFF
private const val SHIFT_RED = 16
private const val SHIFT_GREEN = 8
private const val SHADOW_BLUR_DP = 3f
private const val SHADOW_DY_DP = 1.5f
private const val SHADOW_ALPHA = 0x48
private const val RANK_SELECTED_BOOST = 1_000
private const val BADGE_RADIUS_DP = 5f
private const val BADGE_RING_DP = 1.25f
private const val BADGE_CLOCK_RADIUS_DP = 3f
private const val BADGE_CLOCK_STROKE_DP = 1f
private const val DIAGONAL = 0.7071f
private const val RADIX_HEX = 16

/** Everything a marker/chip bitmap needs besides its own content. */
data class MarkerStyle(val density: Float, val palette: MapPalette)

/**
 * Index of the stop whose drawn disc is under [point], or null. Pure geometry
 * ([hitWaypoint]) rather than queryRenderedFeatures: the icon quad carries
 * shadow + grow headroom, so a feature query hit ~2.7× the visible disc.
 */
internal fun MapLibreMap.waypointIndexAt(
    point: MapLibreLatLng,
    waypoints: List<Waypoint>,
    selectedIndex: Int?,
    density: Float,
): Int? {
    val tap = projection.toScreenLocation(point).toOffset()
    val markers = waypoints.map { projection.toScreenLocation(it.position.toMapLibre()).toOffset() }
    return hitWaypoint(tap, markers, selectedIndex, density)
}

private fun PointF.toOffset() = Offset(x, y)

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
        val selected = index == selectedIndex
        val hasWait = waypoint.waitSeconds > 0
        // Selection and wait flags are part of the key: addImage caches by
        // name, so a same-named icon would keep showing the stale bitmap.
        val icon = "waypoint-$role-${index + 1}" +
            (if (selected) "-sel" else "") +
            (if (hasWait) "-wait" else "") +
            "-$paletteTag"
        style.addImage(icon, waypointBitmap(role, index + 1, selected, hasWait, markerStyle))
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
    selected: Boolean,
    hasWait: Boolean,
    markerStyle: MarkerStyle,
): Bitmap {
    val (density, palette) = markerStyle
    val fillRadius = WAYPOINT_RADIUS_DP * density
    val stroke = WAYPOINT_STROKE_DP * density
    val grow = SELECTED_GROW_DP * density
    val shadowBlur = SHADOW_BLUR_DP * density
    val shadowDy = SHADOW_DY_DP * density
    // Every variant reserves grow + shadow headroom: uniform bitmap size keeps
    // the icon's center anchor fixed, so selecting never shifts the marker.
    val size = ceil((fillRadius + grow + stroke + shadowBlur + shadowDy) * 2).toInt()
    val center = size / 2f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    // Strava's markers sit on a soft shadow, like the pills over the map.
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(SHADOW_ALPHA, 0, 0, 0)
        maskFilter = BlurMaskFilter(shadowBlur, BlurMaskFilter.Blur.NORMAL)
    }
    // Selected: the disc itself lifts to the selection colour and grows a
    // touch — no halo (Ethan, session 17).
    val radius = if (selected) fillRadius + grow else fillRadius
    canvas.drawCircle(center, center + shadowDy, radius + stroke, shadow)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = when {
        selected -> palette.selection
        role == "start" -> palette.stopStart
        role == "end" -> palette.stopEnd
        else -> palette.stopVia
    }
    val fill = paint.color
    canvas.drawCircle(center, center, radius, paint)
    paint.color = palette.stopRing
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = stroke
    canvas.drawCircle(center, center, radius + stroke / 2f, paint)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = contrastInk(fill, palette.stopText, palette.stopRing)
        typeface = Typeface.DEFAULT_BOLD
        textSize = WAYPOINT_TEXT_DP * density
        textAlign = Paint.Align.CENTER
    }
    val baseline = center - (text.ascent() + text.descent()) / 2f
    canvas.drawText(number.toString(), center, baseline, text)
    if (hasWait) drawWaitBadge(canvas, center, radius + stroke / 2f, markerStyle)
    return bitmap
}

/**
 * A small clock at the disc's top-right says "this stop waits" without the
 * amount — the amount only appears (as a chip) while playback dwells there.
 * The badge sits on the ring's circumference, inside the shadow headroom, so
 * the bitmap size — and the anchor — never change.
 */
private fun drawWaitBadge(canvas: Canvas, center: Float, ringRadius: Float, markerStyle: MarkerStyle) {
    val (density, palette) = markerStyle
    val cx = center + ringRadius * DIAGONAL
    val cy = center - ringRadius * DIAGONAL
    val badgeRadius = BADGE_RADIUS_DP * density
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.stopRing }
    canvas.drawCircle(cx, cy, badgeRadius + BADGE_RING_DP * density, ring)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.chip }
    canvas.drawCircle(cx, cy, badgeRadius, fill)
    val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.chipText
        style = Paint.Style.STROKE
        strokeWidth = BADGE_CLOCK_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
    }
    drawClockGlyph(canvas, cx, cy, BADGE_CLOCK_RADIUS_DP * density, glyph)
}

/**
 * Whichever of the two inks reads on [fill]: the palette's text colour is
 * tuned for the via/start discs, but the selection tint is near-white in
 * dark mode, where the ring colour (the ground) is the legible one.
 */
internal fun contrastInk(fill: Int, text: Int, ring: Int): Int {
    val fillLight = luminance(fill) > LUMINANCE_THRESHOLD
    val textLight = luminance(text) > LUMINANCE_THRESHOLD
    return if (fillLight == textLight) ring else text
}

// Bit maths, not android.graphics.Color: keeps this unit-testable on the JVM.
private fun luminance(argb: Int): Double {
    val r = (argb shr SHIFT_RED) and CHANNEL_MASK
    val g = (argb shr SHIFT_GREEN) and CHANNEL_MASK
    val b = argb and CHANNEL_MASK
    return (LUMA_R * r + LUMA_G * g + LUMA_B * b) / CHANNEL_MAX
}
