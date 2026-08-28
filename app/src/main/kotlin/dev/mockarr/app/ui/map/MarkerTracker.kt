package dev.mockarr.app.ui.map

import android.view.View
import androidx.compose.ui.geometry.Offset
import dev.mockarr.core.model.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Feeds the window position of one tracked map point to Compose so a popover
 * can ride a marker: recomputed on every camera frame and whenever the target
 * changes. Null when nothing is tracked or the point is off the map.
 */
internal class MarkerTracker(
    private val map: MapLibreMap,
    private val view: View,
    private val onChange: (Offset?) -> Unit,
) {
    private var target: LatLng? = null
    private var last: Offset? = null

    init {
        map.addOnCameraMoveListener { publish() }
    }

    fun track(position: LatLng?) {
        target = position
        publish()
    }

    private fun publish() {
        val next = target?.let { map.windowPointOf(view, it) }?.takeIf { it.onScreen() }
        if (next != last) {
            last = next
            onChange(next)
        }
    }

    private fun Offset.onScreen(): Boolean {
        val origin = view.windowOrigin()
        return x >= origin.x && y >= origin.y &&
            x <= origin.x + view.width && y <= origin.y + view.height
    }
}

/** Screen point of [position] in window pixels (the map view may sit under an inset). */
internal fun MapLibreMap.windowPointOf(view: View, position: LatLng): Offset {
    val screen = projection.toScreenLocation(position.toMapLibre())
    val origin = view.windowOrigin()
    return Offset(screen.x + origin.x, screen.y + origin.y)
}

private fun View.windowOrigin(): Offset {
    val location = IntArray(2)
    getLocationInWindow(location)
    return Offset(location[0].toFloat(), location[1].toFloat())
}
