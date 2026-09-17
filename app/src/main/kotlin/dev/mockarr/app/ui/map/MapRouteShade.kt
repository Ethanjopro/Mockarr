package dev.mockarr.app.ui.map

import dev.mockarr.app.ui.theme.MapPalette
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory

/*
 * The route line's shade along its length: the stretch already driven goes
 * quiet behind the puck, and the dark theme's glow goes out with it. Driven by
 * `line-progress`, which the route source provides via line metrics.
 */

/** Width of the seam where the travelled tone meets the accent, as a fraction of the line. */
private const val FEATHER = 0.004f
private const val MIN_SPAN = 0.0001f
private const val RGB_MASK = 0x00FFFFFF

/** A soft halo under the route — the neon road of the dark theme (transparent by day). */
internal fun routeGlowLayer(id: String, source: String, width: Expression): LineLayer =
    LineLayer(id, source).withProperties(
        PropertyFactory.lineWidth(width),
        PropertyFactory.lineBlur(width),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )

/**
 * [behind] up to [progress] (0–1 of the line), feathering into [ahead] over a
 * few metres so the seam glides under the puck instead of cutting. `null`
 * progress (not driving, or a route with off-road spans where line-progress is
 * per piece) paints the whole line in [ahead].
 */
internal fun routeGradient(behind: Int, ahead: Int, progress: Float?): Expression {
    val (low, high) = shadeStops(progress)
    return Expression.interpolate(
        Expression.linear(),
        Expression.lineProgress(),
        Expression.stop(low, Expression.color(behind)),
        Expression.stop(high, Expression.color(ahead)),
    )
}

/**
 * Where the seam sits: the travelled tone ends at `first`, the accent begins
 * at `second`. Always strictly increasing inside 0–1 (MapLibre rejects equal
 * stops), and a null progress puts the seam at the very start.
 */
internal fun shadeStops(progress: Float?): Pair<Float, Float> {
    val boundary = (progress ?: 0f).coerceIn(0f, 1f)
    val low = (boundary - FEATHER).coerceAtLeast(0f)
    val high = maxOf(boundary, low + MIN_SPAN).coerceAtMost(1f)
    return low to high
}

/** Shade the route line and its glow at [progress] in [palette]'s tones. */
internal fun applyRouteShade(style: Style, palette: MapPalette, progress: Float?) {
    style.getLayer(ROUTE_LAYER)?.setProperties(
        PropertyFactory.lineGradient(routeGradient(palette.routeTravelled, palette.route, progress)),
    )
    val glowOut = palette.routeGlow and RGB_MASK
    style.getLayer(ROUTE_GLOW_LAYER)?.setProperties(
        PropertyFactory.lineGradient(routeGradient(glowOut, palette.routeGlow, progress)),
    )
}
