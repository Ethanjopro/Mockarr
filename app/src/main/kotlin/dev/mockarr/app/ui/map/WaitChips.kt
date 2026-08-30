package dev.mockarr.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import dev.mockarr.core.model.Waypoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import kotlin.math.ceil

/** Lift from the waypoint's coordinate to the chip's bottom edge. */
internal const val WAIT_CHIP_LIFT_DP = 18f

private const val CHIP_TEXT_DP = 11f
private const val CHIP_CLOCK_RADIUS_DP = 4.5f
private const val CHIP_CLOCK_STROKE_DP = 1.2f
private const val CHIP_PAD_HORIZONTAL_DP = 7f
private const val CHIP_PAD_VERTICAL_DP = 4f
private const val CHIP_GAP_DP = 4f
private const val MINUTE_HAND_FRACTION = 0.55f
private const val HOUR_HAND_FRACTION = 0.4f
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val RADIX_HEX = 16

/** The stop playback is dwelling at right now, driving that chip's countdown. */
data class ActiveDwell(val waypointIndex: Int, val secondsLeft: Int)

/**
 * One amber countdown chip above the stop playback is dwelling at — and only
 * that one; a configured wait shows as the marker's clock badge instead, so the
 * amount appears exactly while it counts down. Countdown chips mint a new image
 * name each second, so [liveIcons] tracks what this style currently holds and
 * stale images are removed (re-adding under the same name shows the stale
 * bitmap — repo lore from the marker wait badges).
 */
internal fun updateWaitChips(
    style: Style,
    waypoints: List<Waypoint>,
    activeDwell: ActiveDwell?,
    markerStyle: MarkerStyle,
    liveIcons: MutableSet<String>,
) {
    val used = mutableSetOf<String>()
    val paletteTag = markerStyle.palette.hashCode().toUInt().toString(RADIX_HEX)
    val dwellStop = activeDwell?.let { dwell -> waypoints.getOrNull(dwell.waypointIndex) }
    val features = if (activeDwell == null || dwellStop == null || dwellStop.waitSeconds <= 0) {
        emptyList()
    } else {
        val text = formatChipCountdown(activeDwell.secondsLeft)
        val icon = "wait-chip-$text-live-$paletteTag"
        style.addImage(icon, waitChipBitmap(text, markerStyle))
        used += icon
        val feature = Feature.fromGeometry(dwellStop.position.toPoint()).apply {
            addStringProperty(ICON_KEY, icon)
        }
        listOf(feature)
    }
    (liveIcons - used).forEach { style.removeImage(it) }
    liveIcons.clear()
    liveIcons += used
    style.getSourceAs<GeoJsonSource>(WAIT_CHIP_SOURCE)
        ?.setGeoJson(FeatureCollection.fromFeatures(features))
}

/** "0:45" / "4:32" / "1:02:05" — a live countdown; formatDurationShort is too coarse for one. */
internal fun formatChipCountdown(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
    val minutes = total / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
    val secs = total % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

/** Amber rounded pill with a hand-drawn clock glyph and bold countdown text. */
private fun waitChipBitmap(text: String, markerStyle: MarkerStyle): Bitmap {
    val (density, palette) = markerStyle
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.chipActiveText
        typeface = Typeface.DEFAULT_BOLD
        textSize = CHIP_TEXT_DP * density
    }
    val clockRadius = CHIP_CLOCK_RADIUS_DP * density
    val padH = CHIP_PAD_HORIZONTAL_DP * density
    val padV = CHIP_PAD_VERTICAL_DP * density
    val gap = CHIP_GAP_DP * density
    val textWidth = textPaint.measureText(text)
    val contentHeight = maxOf(textPaint.descent() - textPaint.ascent(), clockRadius * 2)
    val width = ceil(padH * 2 + clockRadius * 2 + gap + textWidth).toInt()
    val height = ceil(padV * 2 + contentHeight).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.chipActive
    }
    val corner = height / 2f
    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), corner, corner, pill)
    val centerX = padH + clockRadius
    val centerY = height / 2f
    val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPaint.color
        style = Paint.Style.STROKE
        strokeWidth = CHIP_CLOCK_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
    }
    drawClockGlyph(canvas, centerX, centerY, clockRadius, glyph)
    val baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(text, padH + clockRadius * 2 + gap, baseline, textPaint)
    return bitmap
}

/** Circle with two hands (minute up, hour right), stroked with [paint]. */
internal fun drawClockGlyph(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
    canvas.drawCircle(cx, cy, radius, paint)
    canvas.drawLine(cx, cy, cx, cy - radius * MINUTE_HAND_FRACTION, paint)
    canvas.drawLine(cx, cy, cx + radius * HOUR_HAND_FRACTION, cy, paint)
}
