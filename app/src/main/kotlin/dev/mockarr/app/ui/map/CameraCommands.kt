package dev.mockarr.app.ui.map

import dev.mockarr.core.model.LatLng
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

// Fit padding is asymmetric because the map runs edge-to-edge BEHIND the
// overlays: a uniform padding parks the route's extremes under the search bar
// (top) or the card stack + navigation bar (bottom), where they fit
// geometrically but are invisible. Values are dp, converted at apply time.
private const val FIT_PADDING_SIDE_DP = 32f
private const val FIT_PADDING_TOP_DP = 120f
private const val FIT_PADDING_BOTTOM_DP = 180f

internal fun applyCameraCommand(map: MapLibreMap, command: CameraCommand, density: Float, animate: Boolean) {
    when (command) {
        is CameraCommand.Center -> map.move(
            CameraUpdateFactory.newLatLngZoom(command.target.toMapLibre(), command.zoom),
            animate,
        )
        is CameraCommand.FitRoute -> fitRoute(map, command.points, density, animate)
        is CameraCommand.EnsureVisible -> ensureVisible(map, command.points, density, animate)
    }
}

/** Honour the system "Remove animations" setting: MapLibre doesn't read it. */
internal fun MapLibreMap.move(update: CameraUpdate, animate: Boolean, durationMillis: Int = DEFAULT_EASE_MILLIS) {
    if (animate) easeCamera(update, durationMillis) else moveCamera(update)
}

private const val DEFAULT_EASE_MILLIS = 300

private fun fitRoute(map: MapLibreMap, points: List<LatLng>, density: Float, animate: Boolean) {
    val bounds = boundsOf(points) ?: return
    val fit = map.getCameraForLatLngBounds(bounds, fitPadding(density)) ?: return
    map.move(CameraUpdateFactory.newCameraPosition(fit), animate)
}

/**
 * Builder-mode reframe: leaves the camera alone while every point stays inside
 * the overlay-padded viewport, and never zooms in when it does move — the old
 * unconditional bounds fit slammed the zoom in on nearby stops after every tap.
 */
private fun ensureVisible(map: MapLibreMap, points: List<LatLng>, density: Float, animate: Boolean) {
    val bounds = boundsOf(points) ?: return
    if (allPointsInView(map, points, density)) return
    val fit = map.getCameraForLatLngBounds(bounds, fitPadding(density))
    val target = fit?.target
    if (target != null) {
        val zoom = minOf(fit.zoom, map.cameraPosition.zoom)
        map.move(CameraUpdateFactory.newLatLngZoom(target, zoom), animate)
    }
}

// Same margins as the fit padding, so a just-fitted route counts as in view.
private fun allPointsInView(map: MapLibreMap, points: List<LatLng>, density: Float): Boolean {
    val side = FIT_PADDING_SIDE_DP * density
    val top = FIT_PADDING_TOP_DP * density
    val bottom = FIT_PADDING_BOTTOM_DP * density
    return points.all { point ->
        val screen = map.projection.toScreenLocation(point.toMapLibre())
        screen.x >= side && screen.x <= map.width - side &&
            screen.y >= top && screen.y <= map.height - bottom
    }
}

private fun fitPadding(density: Float): IntArray = intArrayOf(
    (FIT_PADDING_SIDE_DP * density).toInt(),
    (FIT_PADDING_TOP_DP * density).toInt(),
    (FIT_PADDING_SIDE_DP * density).toInt(),
    (FIT_PADDING_BOTTOM_DP * density).toInt(),
)

private fun boundsOf(points: List<LatLng>): LatLngBounds? {
    // LatLngBounds.Builder.build() throws below 2 points.
    if (points.size < 2) return null
    val builder = LatLngBounds.Builder()
    points.forEach { builder.include(it.toMapLibre()) }
    return builder.build()
}
