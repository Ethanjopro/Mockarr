package dev.mockarr.app.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import dev.mockarr.app.ui.theme.MapPalette
import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow

/*
 * The mocked position as a nav puck: a see-through circle showing how far the
 * GPS wobble can scatter, steady on the true route position, and the accent
 * dot on top at the position other apps are actually told (a CircleLayer in
 * MockarrMap) — so the dot jitters inside the range while the range glides
 * down the road. Fixes arrive at the engine's tick rate; both glide between
 * them (and the road shade behind moves with them) so a drive reads as motion
 * rather than a dot teleporting once a second.
 */

internal const val PLAYBACK_RANGE_LAYER = "playback-range-layer"
internal const val PIN_RANGE_LAYER = "pin-range-layer"
internal const val PUCK_KIND_KEY = "kind"
internal const val PUCK_DOT = "dot"
internal const val PUCK_RANGE = "range"
private const val RANGE_FILL_OPACITY = 0.15f
private const val RANGE_STROKE_OPACITY = 0.4f
private const val RANGE_STROKE_WIDTH = 1f

/** Never smaller than this on screen: the dot (7 + 3 ring) always sits inside a visible range. */
internal const val RANGE_FLOOR_DP = 16f
private const val RANGE_MAX_ZOOM = 24f

/** Metres per dp at zoom 0 on the equator: MapLibre's world is 512 dp wide at z0. */
private const val METERS_PER_DP_Z0 = 78_271.517
private const val MIN_COS_LATITUDE = 0.01

/** 2σ holds 95 % of the half-normal wobble offsets (Jitter): "where the dot can be". */
private const val RANGE_SIGMAS = 2.0
private const val GLIDE_MIN_MILLIS = 150L
private const val GLIDE_MAX_MILLIS = 1_500L
private const val GLIDE_TELEPORT_METERS = 200.0

/**
 * Fixes come at least every 2 s (0.5 Hz, the slowest tick setting); a gap well past that
 * means the app was away or the drive paused.
 */
private const val STALE_FIX_MILLIS = 5_000L

/** The puck's two points: the reported fix (the dot) and the true position its range is centred on. */
internal fun playbackFeatures(position: LatLng?, center: LatLng?): FeatureCollection {
    fun pointOf(at: LatLng, kind: String) = Feature.fromGeometry(at.toPoint()).apply {
        addStringProperty(PUCK_KIND_KEY, kind)
    }
    val features = listOfNotNull(
        position?.let { pointOf(it, PUCK_DOT) },
        (center ?: position)?.let { pointOf(it, PUCK_RANGE) },
    )
    return FeatureCollection.fromFeatures(features)
}

/** The wobble's on-map radius in metres, or null when there is no wobble to show. */
internal fun wobbleRadiusOf(enabled: Boolean, sigmaMeters: Double): Double? =
    if (enabled && sigmaMeters > 0.0) sigmaMeters * RANGE_SIGMAS else null

/**
 * Zoom stops for a circle [radiusMeters] across on the ground at [latitude],
 * floored at [floorDp]. For `interpolate(exponential(2), zoom, …)`: past the
 * crossover the segment to [maxZoom] evaluates to exactly k·2^z (metre-true);
 * below it the circle holds the floor. `max(floor, …)` would be simpler, but
 * MapLibre only allows `zoom` directly inside a top-level interpolate/step.
 */
internal fun wobbleRadiusStops(
    radiusMeters: Double,
    latitude: Double,
    floorDp: Float = RANGE_FLOOR_DP,
    maxZoom: Float = RANGE_MAX_ZOOM,
): List<Pair<Float, Float>> {
    val metersPerDp = METERS_PER_DP_Z0 * cos(Math.toRadians(latitude)).coerceAtLeast(MIN_COS_LATITUDE)
    val atZoomZero = radiusMeters / metersPerDp
    val atMax = (atZoomZero * 2.0.pow(maxZoom.toDouble())).toFloat()
    val crossover = (ln(floorDp / atZoomZero) / ln(2.0)).toFloat()
    return when {
        crossover >= maxZoom -> listOf(0f to floorDp, maxZoom to floorDp)
        crossover <= 0f -> listOf(0f to atZoomZero.toFloat(), maxZoom to atMax)
        else -> listOf(0f to floorDp, crossover to floorDp, maxZoom to atMax)
    }
}

/**
 * Size both range circles (the drive's and the held pin's) for [radiusMeters]
 * at [latitude], or hide them when there is no wobble.
 */
internal fun applyWobbleRadius(style: Style, radiusMeters: Double?, latitude: Double) {
    val layers = listOf(PLAYBACK_RANGE_LAYER, PIN_RANGE_LAYER).mapNotNull { style.getLayer(it) }
    if (radiusMeters == null) {
        layers.forEach { it.setProperties(PropertyFactory.visibility(Property.NONE)) }
        return
    }
    val circleRadius = PropertyFactory.circleRadius(wobbleRadiusExpression(radiusMeters, latitude))
    layers.forEach { it.setProperties(PropertyFactory.visibility(Property.VISIBLE), circleRadius) }
}

/** `interpolate(exponential(2), zoom, …)` over [wobbleRadiusStops], without a vararg spread. */
private fun wobbleRadiusExpression(radiusMeters: Double, latitude: Double): Expression {
    val stops = wobbleRadiusStops(radiusMeters, latitude).map { (zoom, radius) -> Expression.stop(zoom, radius) }
    val interpolation = Expression.exponential(2f)
    val zoom = Expression.zoom()
    return if (stops.size == 2) {
        Expression.interpolate(interpolation, zoom, stops[0], stops[1])
    } else {
        Expression.interpolate(interpolation, zoom, stops[0], stops[1], stops[2])
    }
}

/** How long a glide takes: the real interval between fixes, clamped so a stall never crawls. */
internal fun glideMillis(intervalMillis: Long): Int =
    intervalMillis.coerceIn(GLIDE_MIN_MILLIS, GLIDE_MAX_MILLIS).toInt()

/**
 * True when what the map drew at the last fix is out of date: coming back to the app, the
 * puck, its road shade and the follow camera jump to now. Gliding from where they were when
 * the app left replayed the stretch driven meanwhile, and the shade seemed to reset.
 */
internal fun isStaleGap(gapMillis: Long): Boolean = gapMillis > STALE_FIX_MILLIS

/**
 * Where a fix puts the puck: the reported [position] (the dot), the true
 * [center] of its wobble range, and 0–1 of the route driven.
 */
internal data class PuckFix(val position: LatLng?, val center: LatLng?, val progress: Float?)

/**
 * What the puck currently shows on the map — dot, range and road shade — and
 * the glides between fixes. Every write goes straight to the style from an
 * animation frame callback; nothing here is Compose state.
 */
internal class PuckMotion {
    var position: LatLng? = null
        private set
    var center: LatLng? = null
        private set
    var progress: Float? = null
        private set
    var palette: MapPalette? = null
    private var lastFixAt: Long = 0L

    /** Redraw the puck and the road shade where they are (a theme change repaints in place). */
    fun repaint(style: Style) {
        style.getSourceAs<GeoJsonSource>(PLAYBACK_SOURCE)?.setGeoJson(playbackFeatures(position, center))
        palette?.let { applyRouteShade(style, it, progress) }
    }

    /**
     * Take the puck to the next fix. With animations on and a previous point to
     * leave from, the dot and its range glide there over the interval the fixes
     * are arriving at, starting from wherever they were drawn last — an early
     * fix cuts the glide short instead of snapping. A first fix, a jump
     * (resume, restart), a return to the app ([isStaleGap]) or reduce-motion
     * sets it directly.
     */
    suspend fun moveTo(style: Style, fix: PuckFix, animate: Boolean, nowMillis: Long) {
        val from = position
        val fromCenter = center ?: from
        val target = fix.position
        val targetCenter = fix.center ?: target
        val interval = nowMillis - lastFixAt
        lastFixAt = nowMillis
        val direct = from == null || fromCenter == null || target == null || targetCenter == null || !animate ||
            isStaleGap(interval) || GeoMath.distanceMeters(from, target) > GLIDE_TELEPORT_METERS
        if (direct) {
            position = target
            center = targetCenter
            progress = fix.progress
            repaint(style)
            return
        }
        val fromProgress = progress ?: fix.progress
        Animatable(0f).animateTo(1f, tween(glideMillis(interval), easing = LinearEasing)) {
            val fraction = value.toDouble()
            position = lerp(from, target, fraction)
            center = lerp(fromCenter, targetCenter, fraction)
            progress = fix.progress?.let { to -> fromProgress?.let { it + (to - it) * value } ?: to }
            repaint(style)
        }
    }

    private fun lerp(from: LatLng, to: LatLng, fraction: Double) = LatLng(
        from.latitude + (to.latitude - from.latitude) * fraction,
        from.longitude + (to.longitude - from.longitude) * fraction,
    )
}

/**
 * A see-through disc in the position accent with a hairline rim: the wobble's
 * reach. [kind] filters one feature of a two-feature source; null draws every
 * feature (the held pin's single point). Radius comes from [applyWobbleRadius].
 */
internal fun rangeLayer(id: String, source: String, kind: String?): CircleLayer =
    CircleLayer(id, source).withProperties(
        PropertyFactory.circleRadius(RANGE_FLOOR_DP),
        PropertyFactory.circleOpacity(RANGE_FILL_OPACITY),
        PropertyFactory.circleStrokeWidth(RANGE_STROKE_WIDTH),
        PropertyFactory.circleStrokeOpacity(RANGE_STROKE_OPACITY),
        PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
        PropertyFactory.circlePitchScale(Property.CIRCLE_PITCH_SCALE_MAP),
    ).apply {
        if (kind != null) setFilter(Expression.eq(Expression.get(PUCK_KIND_KEY), kind))
    }
