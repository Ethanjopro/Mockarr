package dev.mockarr.app.ui.map

import androidx.compose.ui.geometry.Offset

/**
 * A finger's minimum target, as a radius (Material's 48dp): the drawn disc is only
 * ~27dp across, which a thumb misses (audit, session 49).
 */
internal const val WAYPOINT_TOUCH_RADIUS_DP = 24f

/**
 * Index of the stop [tap] lands on, or null. A tap on a drawn disc (fill + ring;
 * the selected one is grown) wins, overlaps resolving the way the renderer draws
 * them: selected on top, then the higher index. Off every disc, the nearest stop
 * within [WAYPOINT_TOUCH_RADIUS_DP] is the target. Screen pixels in, so this stays
 * JVM-testable.
 */
internal fun hitWaypoint(
    tap: Offset,
    markers: List<Offset>,
    selectedIndex: Int?,
    density: Float,
): Int? {
    val base = (WAYPOINT_RADIUS_DP + WAYPOINT_STROKE_DP) * density
    val grown = base + SELECTED_GROW_DP * density
    fun distance(index: Int) = (tap - markers[index]).getDistance()
    val onDisc = markers.indices.filter { index ->
        distance(index) <= if (index == selectedIndex) grown else base
    }
    if (onDisc.isNotEmpty()) return onDisc.firstOrNull { it == selectedIndex } ?: onDisc.max()
    val reach = WAYPOINT_TOUCH_RADIUS_DP * density
    return markers.indices.filter { distance(it) <= reach }.minByOrNull(::distance)
}
