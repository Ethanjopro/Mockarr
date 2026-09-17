package dev.mockarr.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import dev.mockarr.app.ui.theme.MapPalette
import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import kotlin.math.ceil

/*
 * The mocked position as a nav puck: a soft halo, a heading beam that turns
 * with the fix's bearing, and the accent disc on top (a CircleLayer in
 * MockarrMap). Fixes arrive at the engine's tick rate; the puck glides
 * between them (and the road shade behind it moves with it) so a drive reads
 * as motion rather than a dot teleporting once a second.
 */

internal const val PLAYBACK_HALO_LAYER = "playback-halo-layer"
internal const val PLAYBACK_HEADING_LAYER = "playback-heading-layer"
internal const val PLAYBACK_HEADING_ICON = "playback-heading"
internal const val BEARING_KEY = "bearing"
private const val HALO_RADIUS = 16f
private const val HALO_OPACITY = 0.22f
private const val CONE_SIZE_DP = 88f
private const val CONE_HALF_ANGLE_DEGREES = 38f
private const val UP_DEGREES = -90f
private const val CONE_ALPHA = 0xC0
private const val CONE_TIP_ALPHA = 0x00
private const val CONE_INNER_STOP = 0.35f
private const val BREATH_LOW = 0.7f
private const val BREATH_HIGH = 1.0f
private const val BREATH_MILLIS = 1_600
private const val SETTLE_MILLIS = 600
private const val CHANNEL_MASK = 0xFF
private const val ALPHA_SHIFT = 24
private const val GLIDE_MIN_MILLIS = 150L
private const val GLIDE_MAX_MILLIS = 1_500L
private const val GLIDE_TELEPORT_METERS = 200.0
private const val HALF_TURN_DEGREES = 180.0
private const val FULL_TURN_DEGREES = 360.0

/** The puck's feature: its point plus the bearing the beam rotates to. */
internal fun playbackFeatures(position: LatLng?, bearingDegrees: Double?): FeatureCollection {
    val feature = position?.let {
        Feature.fromGeometry(it.toPoint()).apply {
            addNumberProperty(BEARING_KEY, bearingDegrees ?: 0.0)
        }
    }
    return if (feature == null) FeatureCollection.fromFeatures(emptyList()) else FeatureCollection.fromFeature(feature)
}

/** How long a glide takes: the real interval between fixes, clamped so a stall never crawls. */
internal fun glideMillis(intervalMillis: Long): Int =
    intervalMillis.coerceIn(GLIDE_MIN_MILLIS, GLIDE_MAX_MILLIS).toInt()

/** Bearing interpolation along the shorter arc: 350° → 10° passes through 0°, not 180°. */
internal fun lerpBearing(from: Double, to: Double, fraction: Double): Double {
    var delta = (to - from) % FULL_TURN_DEGREES
    if (delta > HALF_TURN_DEGREES) delta -= FULL_TURN_DEGREES
    if (delta < -HALF_TURN_DEGREES) delta += FULL_TURN_DEGREES
    val result = (from + delta * fraction) % FULL_TURN_DEGREES
    return if (result < 0) result + FULL_TURN_DEGREES else result
}

/** Where a fix puts the puck: its point, the bearing the beam turns to, and 0–1 of the route driven. */
internal data class PuckFix(val position: LatLng?, val bearing: Double?, val progress: Float?)

/**
 * What the puck currently shows on the map — position, bearing, road shade and
 * beam opacity — and the glides between fixes. Every write goes straight to the
 * style from an animation frame callback; nothing here is Compose state.
 */
internal class PuckMotion {
    var position: LatLng? = null
        private set
    var bearing: Double = 0.0
        private set
    var progress: Float? = null
        private set
    var palette: MapPalette? = null
    private var lastFixAt: Long = 0L
    private val beamOpacity = Animatable(BREATH_HIGH)

    /** Redraw the puck and the road shade where they are (a theme change repaints in place). */
    fun repaint(style: Style) {
        style.getSourceAs<GeoJsonSource>(PLAYBACK_SOURCE)?.setGeoJson(playbackFeatures(position, bearing))
        palette?.let { applyRouteShade(style, it, progress) }
    }

    /**
     * Take the puck to the next fix. With animations on and a previous point to
     * leave from, it glides there over the interval the fixes are arriving at,
     * starting from wherever it was drawn last — an early fix cuts the glide
     * short instead of snapping. A first fix, a jump (resume, restart) or
     * reduce-motion sets it directly.
     */
    suspend fun moveTo(style: Style, fix: PuckFix, animate: Boolean, nowMillis: Long) {
        val from = position
        val target = fix.position
        val interval = nowMillis - lastFixAt
        lastFixAt = nowMillis
        val direct = from == null || target == null || !animate ||
            GeoMath.distanceMeters(from, target) > GLIDE_TELEPORT_METERS
        if (direct) {
            position = target
            bearing = fix.bearing ?: bearing
            progress = fix.progress
            repaint(style)
            return
        }
        val fromBearing = bearing
        val toBearing = fix.bearing ?: fromBearing
        val fromProgress = progress ?: fix.progress
        Animatable(0f).animateTo(1f, tween(glideMillis(interval), easing = LinearEasing)) {
            val fraction = value.toDouble()
            position = LatLng(
                from.latitude + (target.latitude - from.latitude) * fraction,
                from.longitude + (target.longitude - from.longitude) * fraction,
            )
            bearing = lerpBearing(fromBearing, toBearing, fraction)
            progress = fix.progress?.let { to -> fromProgress?.let { it + (to - it) * value } ?: to }
            repaint(style)
        }
    }

    /**
     * The beam breathes while the drive is moving — the one pulse the brief
     * allows — and settles dim while it is paused or waiting at a stop, like an
     * engine idling. Reduce-motion holds it steady at the target instead.
     * Runs until cancelled.
     */
    suspend fun breathe(layer: Layer, moving: Boolean, animate: Boolean) {
        fun apply(value: Float) = layer.setProperties(PropertyFactory.iconOpacity(value))
        if (!animate) {
            val steady = if (moving) BREATH_HIGH else BREATH_LOW
            beamOpacity.snapTo(steady)
            apply(steady)
            return
        }
        if (!moving) {
            beamOpacity.animateTo(BREATH_LOW, tween(SETTLE_MILLIS, easing = EaseInOutSine)) { apply(value) }
            return
        }
        while (true) {
            beamOpacity.animateTo(BREATH_LOW, tween(BREATH_MILLIS, easing = EaseInOutSine)) { apply(value) }
            beamOpacity.animateTo(BREATH_HIGH, tween(BREATH_MILLIS, easing = EaseInOutSine)) { apply(value) }
        }
    }
}

/**
 * A beam pointing up (+y is south on a bitmap, so the tip is at the top): a
 * solid core at the puck, then a long fade to nothing at its rim. Painted in
 * the palette's `heading` tint so it reads over the route line itself.
 * MapLibre rotates it by the feature's bearing with `icon-rotation-alignment: map`.
 */
internal fun headingConeBitmap(color: Int, density: Float): Bitmap {
    val size = ceil(CONE_SIZE_DP * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cx = size / 2f
    val cy = size / 2f
    val radius = size / 2f
    val rgb = color and (CHANNEL_MASK shl ALPHA_SHIFT).inv()
    val solid = rgb or (CONE_ALPHA shl ALPHA_SHIFT)
    val clear = rgb or (CONE_TIP_ALPHA shl ALPHA_SHIFT)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(solid, solid, clear),
            floatArrayOf(0f, CONE_INNER_STOP, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    val wedge = Path().apply {
        moveTo(cx, cy)
        // arcTo sweeps clockwise from the start angle; -90° is straight up.
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
        arcTo(bounds, UP_DEGREES - CONE_HALF_ANGLE_DEGREES, CONE_HALF_ANGLE_DEGREES * 2, false)
        close()
    }
    Canvas(bitmap).drawPath(wedge, paint)
    return bitmap
}

/** A soft accent disc under the puck: the floor that gives the flat dot depth. Static by design. */
internal fun haloLayer(): CircleLayer =
    CircleLayer(PLAYBACK_HALO_LAYER, PLAYBACK_SOURCE).withProperties(
        PropertyFactory.circleRadius(HALO_RADIUS),
        PropertyFactory.circleBlur(1f),
        PropertyFactory.circleOpacity(HALO_OPACITY),
        PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
    )

internal fun headingLayer(): SymbolLayer =
    SymbolLayer(PLAYBACK_HEADING_LAYER, PLAYBACK_SOURCE).withProperties(
        PropertyFactory.iconImage(PLAYBACK_HEADING_ICON),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
        PropertyFactory.iconRotate(Expression.get(BEARING_KEY)),
        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
        PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
    )
