package dev.mockarr.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import dev.mockarr.app.ui.formatDurationShort
import dev.mockarr.core.model.Waypoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import kotlin.math.ceil

/** Lift from the waypoint's coordinate to the chip's bottom edge. */
internal const val WAIT_CHIP_LIFT_DP = 20f

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

// Literal ARGB ints, not Color.argb()/rgb(): the android.jar stubs throw in
// unit tests, and a file-level Color call would break loading this class there.
private val CHIP_COLOR = 0xEB263238.toInt()
private val CHIP_ACTIVE_COLOR = 0xFFF9A825.toInt()

/** The stop playback is dwelling at right now, driving that chip's countdown. */
data class ActiveDwell(val waypointIndex: Int, val secondsLeft: Int)

/**
 * One clock chip above every waited stop: the dwelling stop shows a live
 * countdown on an amber pill, the rest their configured wait. Countdown chips
 * mint a new image name each second, so [liveIcons] tracks what this style
 * currently holds and stale images are removed (re-adding under the same name
 * shows the stale bitmap — repo lore from the marker wait badges).
 */
internal fun updateWaitChips(
    style: Style,
    waypoints: List<Waypoint>,
    activeDwell: ActiveDwell?,
    density: Float,
    liveIcons: MutableSet<String>,
) {
    val used = mutableSetOf<String>()
    val features = waypoints.mapIndexedNotNull { index, waypoint ->
        if (waypoint.waitSeconds <= 0) {
            null
        } else {
            val dwellHere = activeDwell?.takeIf { it.waypointIndex == index }
            val text = if (dwellHere != null) {
                formatChipCountdown(dwellHere.secondsLeft)
            } else {
                formatDurationShort(waypoint.waitSeconds.toDouble())
            }
            val icon = "wait-chip-$text" + if (dwellHere != null) "-live" else ""
            style.addImage(icon, waitChipBitmap(text, dwellHere != null, density))
            used += icon
            Feature.fromGeometry(waypoint.position.toPoint()).apply {
                addStringProperty(ICON_KEY, icon)
            }
        }
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

/** Rounded pill with a hand-drawn clock glyph and bold text, baked at [density]. */
private fun waitChipBitmap(text: String, active: Boolean, density: Float): Bitmap {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (active) Color.BLACK else Color.WHITE
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
        color = if (active) CHIP_ACTIVE_COLOR else CHIP_COLOR
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
    canvas.drawCircle(centerX, centerY, clockRadius, glyph)
    canvas.drawLine(centerX, centerY, centerX, centerY - clockRadius * MINUTE_HAND_FRACTION, glyph)
    canvas.drawLine(centerX, centerY, centerX + clockRadius * HOUR_HAND_FRACTION, centerY, glyph)
    val baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
    canvas.drawText(text, padH + clockRadius * 2 + gap, baseline, textPaint)
    return bitmap
}
