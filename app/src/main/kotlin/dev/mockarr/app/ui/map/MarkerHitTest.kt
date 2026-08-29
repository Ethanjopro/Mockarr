package dev.mockarr.app.ui.map

import androidx.compose.ui.geometry.Offset

/**
 * Index of the stop whose drawn disc contains [tap], or null. The target is
 * exactly the disc (fill + ring; the selected one is grown), and overlaps
 * resolve the way the renderer draws them: selected on top, then the higher
 * index. Screen pixels in, so this stays JVM-testable.
 */
internal fun hitWaypoint(
    tap: Offset,
    markers: List<Offset>,
    selectedIndex: Int?,
    density: Float,
): Int? {
    val base = (WAYPOINT_RADIUS_DP + WAYPOINT_STROKE_DP) * density
    val grown = base + SELECTED_GROW_DP * density
    val hits = markers.indices.filter { index ->
        val radius = if (index == selectedIndex) grown else base
        (tap - markers[index]).getDistance() <= radius
    }
    return hits.firstOrNull { it == selectedIndex } ?: hits.maxOrNull()
}
