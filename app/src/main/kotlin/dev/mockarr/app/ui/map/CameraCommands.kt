package dev.mockarr.app.ui.map

import dev.mockarr.core.model.LatLng
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

// Fit padding is asymmetric because the map runs edge-to-edge BEHIND the
// overlays: a uniform padding parks the route's extremes under the search bar
// (top) or the card stack + navigation bar (bottom), where they fit
// geometrically but are invisible. The bottom stack (sheet peek, stat card,
// builder pills) varies per state and device, so the screen reports its live
// height ([FitPadding.bottomObstructionPx]); the dp floor covers the first
// frames before it is measured. Values are dp, converted at apply time.
private const val FIT_PADDING_SIDE_DP = 32f
private const val FIT_PADDING_TOP_DP = 120f
private const val FIT_PADDING_BOTTOM_MIN_DP = 180f

/** Clearance between the route's lowest point and the topmost bottom overlay. */
private const val FIT_CLEARANCE_DP = 24f

/** Viewport margins a fit must respect, in px. */
internal class FitPadding(density: Float, bottomObstructionPx: Int) {
    val side = (FIT_PADDING_SIDE_DP * density).toInt()
    val top = (FIT_PADDING_TOP_DP * density).toInt()
    val bottom = maxOf(
        (FIT_PADDING_BOTTOM_MIN_DP * density).toInt(),
        bottomObstructionPx + (FIT_CLEARANCE_DP * density).toInt(),
    )

    fun toArray(): IntArray = intArrayOf(side, top, side, bottom)
}

internal fun applyCameraCommand(map: MapLibreMap, command: CameraCommand, padding: FitPadding, animate: Boolean) {
    when (command) {
        is CameraCommand.Center -> centerOn(map, command, padding, animate)
        is CameraCommand.FitRoute -> fitRoute(map, command.points, padding, animate)
        is CameraCommand.EnsureVisible -> ensureVisible(map, command.points, padding, animate)
    }
}

/**
 * A padded centre hands MapLibre the fit margins as camera padding, so the
 * point lands in the middle of the visible map (between the top chrome and
 * the card/sheet stack). Never shift the target by hand: the camera keeps the
 * padding of its last fit, and a manual offset stacked on top of it.
 */
private fun centerOn(map: MapLibreMap, command: CameraCommand.Center, padding: FitPadding, animate: Boolean) {
    val target = command.target.toMapLibre()
    val update = if (command.padded) {
        val side = padding.side.toDouble()
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder(map.cameraPosition)
                .target(target)
                .zoom(command.zoom)
                .padding(side, padding.top.toDouble(), side, padding.bottom.toDouble())
                .build(),
        )
    } else {
        CameraUpdateFactory.newLatLngZoom(target, command.zoom)
    }
    map.move(update, animate)
}

/** Honour the system "Remove animations" setting: MapLibre doesn't read it. */
internal fun MapLibreMap.move(update: CameraUpdate, animate: Boolean, durationMillis: Int = DEFAULT_EASE_MILLIS) {
    if (animate) easeCamera(update, durationMillis) else moveCamera(update)
}

private const val DEFAULT_EASE_MILLIS = 300

private fun fitRoute(map: MapLibreMap, points: List<LatLng>, padding: FitPadding, animate: Boolean) {
    val bounds = boundsOf(points) ?: return
    val fit = map.getCameraForLatLngBounds(bounds, padding.toArray()) ?: return
    map.move(CameraUpdateFactory.newCameraPosition(fit), animate)
}

/**
 * Builder-mode reframe: leaves the camera alone while every point stays inside
 * the overlay-padded viewport at a sensible size, so tapping nearby stops does
 * not slam the zoom after every edit. It does refit — zooming in or out — when
 * a point is out of view or the route has become a speck ([ZOOM_IN_SLACK]
 * levels smaller than its fit; Ethan, session 19: "the route doesn't zoom").
 */
private fun ensureVisible(map: MapLibreMap, points: List<LatLng>, padding: FitPadding, animate: Boolean) {
    val bounds = boundsOf(points) ?: return
    val fit = map.getCameraForLatLngBounds(bounds, padding.toArray()) ?: return
    val speck = fit.zoom - map.cameraPosition.zoom > ZOOM_IN_SLACK
    if (!speck && allPointsInView(map, points, padding)) return
    map.move(CameraUpdateFactory.newCameraPosition(fit), animate)
}

/** A route is "too small" once its fit sits this many zoom levels above the camera (≈ under ⅓ of the viewport). */
private const val ZOOM_IN_SLACK = 1.5

// Same margins as the fit padding, so a just-fitted route counts as in view.
private fun allPointsInView(map: MapLibreMap, points: List<LatLng>, padding: FitPadding): Boolean =
    points.all { point ->
        val screen = map.projection.toScreenLocation(point.toMapLibre())
        screen.x >= padding.side && screen.x <= map.width - padding.side &&
            screen.y >= padding.top && screen.y <= map.height - padding.bottom
    }

private fun boundsOf(points: List<LatLng>): LatLngBounds? {
    // LatLngBounds.Builder.build() throws below 2 points.
    if (points.size < 2) return null
    val builder = LatLngBounds.Builder()
    points.forEach { builder.include(it.toMapLibre()) }
    return builder.build()
}
