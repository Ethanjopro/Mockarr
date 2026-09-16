package dev.mockarr.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import dev.mockarr.app.ui.theme.MapPalette
import dev.mockarr.core.model.LatLng
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import kotlin.math.ceil

/*
 * The mocked position as a nav puck: the accent disc (a CircleLayer in
 * MockarrMap) over a translucent heading cone that rotates with the fix's
 * bearing, and the route line dimming behind it — the two effects that make a
 * drive read as a drive rather than a dot on a line.
 */

internal const val PLAYBACK_HEADING_LAYER = "playback-heading-layer"
internal const val PLAYBACK_HEADING_ICON = "playback-heading"
internal const val BEARING_KEY = "bearing"
private const val CONE_SIZE_DP = 56f
private const val CONE_HALF_ANGLE_DEGREES = 28f
private const val UP_DEGREES = -90f
private const val CONE_ALPHA = 0xB8
private const val CONE_TIP_ALPHA = 0x00
private const val CONE_INNER_STOP = 0.4f
private const val BREATH_LOW = 0.7f
private const val BREATH_HIGH = 1.0f
private const val BREATH_MILLIS = 1_400
private const val CHANNEL_MASK = 0xFF
private const val ALPHA_SHIFT = 24

/** The puck's feature: its point plus the bearing the cone rotates to. */
internal fun playbackFeatures(position: LatLng?, bearingDegrees: Double?): FeatureCollection {
    val feature = position?.let {
        Feature.fromGeometry(it.toPoint()).apply {
            addNumberProperty(BEARING_KEY, bearingDegrees ?: 0.0)
        }
    }
    return if (feature == null) FeatureCollection.fromFeatures(emptyList()) else FeatureCollection.fromFeature(feature)
}

internal fun updatePlayback(style: org.maplibre.android.maps.Style, position: LatLng?, bearingDegrees: Double?) {
    style.getSourceAs<GeoJsonSource>(PLAYBACK_SOURCE)?.setGeoJson(playbackFeatures(position, bearingDegrees))
}

/**
 * A wedge pointing up (+y is south on a bitmap, so the tip is at the top),
 * fading from the accent at the puck to nothing at its rim. MapLibre rotates
 * it by the feature's bearing with `icon-rotation-alignment: map`.
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

internal fun headingLayer(): SymbolLayer =
    SymbolLayer(PLAYBACK_HEADING_LAYER, PLAYBACK_SOURCE).withProperties(
        PropertyFactory.iconImage(PLAYBACK_HEADING_ICON),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
        PropertyFactory.iconRotate(Expression.get(BEARING_KEY)),
        PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
        PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
    )

/** A soft halo under the route — the neon road of the dark theme (transparent by day). */
internal fun routeGlowLayer(id: String, source: String, width: Expression): LineLayer =
    LineLayer(id, source).withProperties(
        PropertyFactory.lineWidth(width),
        PropertyFactory.lineBlur(width),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )

/**
 * The route line's colour along its length: [MapPalette.routeTravelled] up
 * to [progress] (0–1 of the line), the accent beyond. `null` progress (not
 * driving, or a route with off-road spans where line-progress is per piece)
 * paints the whole line in the accent.
 */
internal fun routeGradient(palette: MapPalette, progress: Float?): Expression {
    val boundary = (progress ?: 0f).coerceIn(0f, 1f)
    return Expression.step(
        Expression.lineProgress(),
        Expression.color(palette.routeTravelled),
        Expression.stop(boundary, Expression.color(palette.route)),
    )
}

/**
 * The cone breathes while a drive is on — the one pulse the brief allows —
 * driven from an animation frame callback, never from recomposition. Runs
 * until cancelled; the caller gates it on reduce-motion.
 */
internal suspend fun breathe(layer: Layer) {
    val opacity = Animatable(BREATH_HIGH)
    while (true) {
        opacity.animateTo(BREATH_LOW, tween(BREATH_MILLIS)) {
            layer.setProperties(PropertyFactory.iconOpacity(value))
        }
        opacity.animateTo(BREATH_HIGH, tween(BREATH_MILLIS)) {
            layer.setProperties(PropertyFactory.iconOpacity(value))
        }
    }
}
