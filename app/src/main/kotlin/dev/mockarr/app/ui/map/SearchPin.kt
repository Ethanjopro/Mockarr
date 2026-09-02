package dev.mockarr.app.ui.map

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import dev.mockarr.core.model.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.ceil

internal const val SEARCH_PIN_SOURCE = "search-pin-source"
internal const val SEARCH_PIN_LAYER = "search-pin-layer"
internal const val SEARCH_PIN_ICON = "search-pin"

// The head matches a stop disc (11dp + 2.5dp ring) so the pin reads as the
// same family; the tail reaches 1.6 radii below the head's centre to the point.
private const val PIN_RADIUS_DP = WAYPOINT_RADIUS_DP
private const val PIN_STROKE_DP = WAYPOINT_STROKE_DP
private const val TAIL_FACTOR = 1.6f
private const val DOT_RADIUS_DP = 3.5f
private const val SHADOW_BLUR_DP = 3f
private const val SHADOW_DY_DP = 1.5f
private const val SHADOW_ALPHA = 0x48

// Where the tail leaves the head: the tangent points at (±0.75r, +0.66r) — an
// arc from the right one over the top to the left one, then two lines to the tip.
private const val TANGENT_X = 0.75f
private const val TANGENT_Y = 0.66f
private const val ARC_START_DEGREES = 41.3f
private const val ARC_SWEEP_DEGREES = -262.6f

// Touch: at least the 48dp target, a little slack under the point for a thumb.
private const val TOUCH_TARGET_DP = 48f
private const val TIP_SLACK_DP = 8f

/** The teardrop's drawn height in px, tip at the bitmap's bottom edge (its anchor). */
internal fun searchPinHeightPx(density: Float): Float {
    val radius = PIN_RADIUS_DP * density
    return SHADOW_BLUR_DP * density + PIN_STROKE_DP * density + radius + TAIL_FACTOR * radius + PIN_STROKE_DP * density
}

/** True when a tap at [tap] lands on the pin drawn bottom-anchored at [pin] (screen px). */
internal fun hitSearchPin(tap: Offset, pin: Offset, density: Float): Boolean {
    val halfWidth = maxOf((PIN_RADIUS_DP + PIN_STROKE_DP) * density, TOUCH_TARGET_DP * density / 2f)
    val top = pin.y - searchPinHeightPx(density)
    val bottom = pin.y + TIP_SLACK_DP * density
    return abs(tap.x - pin.x) <= halfWidth && tap.y in top..bottom
}

internal fun MapLibreMap.searchPinAt(
    point: org.maplibre.android.geometry.LatLng,
    pin: LatLng,
    density: Float,
): Boolean {
    val tap = projection.toScreenLocation(point)
    val anchor = projection.toScreenLocation(pin.toMapLibre())
    return hitSearchPin(Offset(tap.x, tap.y), Offset(anchor.x, anchor.y), density)
}

/**
 * A place teardrop in the selection colour on the ground ring, with the stop
 * discs' baked shadow: the exact geocoded point of the last search pick.
 */
internal fun searchPinBitmap(markerStyle: MarkerStyle): Bitmap {
    val (density, palette) = markerStyle
    val radius = PIN_RADIUS_DP * density
    val stroke = PIN_STROKE_DP * density
    val blur = SHADOW_BLUR_DP * density
    val width = ceil((radius + stroke + blur) * 2).toInt()
    val height = ceil(searchPinHeightPx(density)).toInt()
    val cx = width / 2f
    val cy = blur + stroke + radius
    val tipY = height - stroke
    val outline = Path().apply {
        arcTo(RectF(cx - radius, cy - radius, cx + radius, cy + radius), ARC_START_DEGREES, ARC_SWEEP_DEGREES)
        lineTo(cx, tipY)
        lineTo(cx + TANGENT_X * radius, cy + TANGENT_Y * radius)
        close()
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(SHADOW_ALPHA, 0, 0, 0)
        maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.save()
    canvas.translate(0f, SHADOW_DY_DP * density)
    canvas.drawPath(outline, shadow)
    canvas.restore()
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.selection }
    canvas.drawPath(outline, fill)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.stopRing
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(outline, ring)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.stopRing }
    canvas.drawCircle(cx, cy, DOT_RADIUS_DP * density, dot)
    return bitmap
}
