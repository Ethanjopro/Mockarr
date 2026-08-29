package dev.mockarr.app.ui.map

import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.geometry.Offset
import dev.mockarr.core.model.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Owns any touch that starts on a stop marker: a tap selects it, a drag past
 * the touch slop moves it (the map never sees the gesture, so it does not
 * pan). Everything else falls through to MapLibre untouched.
 */
internal class MarkerDragHandler(
    private val map: MapLibreMap,
    private val density: Float,
    private val slopPx: Float,
    private val onTap: (Int) -> Unit,
    private val onDrag: (Int, LatLng) -> Unit,
    private val onDrop: (Int, LatLng) -> Unit,
) : View.OnTouchListener {

    /** Off while playing: markers are inert and the map keeps every gesture. */
    var enabled: Boolean = true

    private var index: Int? = null
    private var down = Offset.Zero
    private var dragging = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val handled = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_UP -> onUp(view, event)
            MotionEvent.ACTION_CANCEL -> onCancel(event)
            else -> false
        }
        return handled
    }

    private fun onDown(event: MotionEvent): Boolean {
        val hit = if (enabled) map.waypointIndexAt(map.latLngAt(event), density) else null
        index = hit
        down = Offset(event.x, event.y)
        dragging = false
        return hit != null
    }

    private fun onMove(event: MotionEvent): Boolean {
        val i = index ?: return false
        if (!dragging && exceedsSlop(down, Offset(event.x, event.y), slopPx)) dragging = true
        if (dragging) onDrag(i, map.latLngAt(event).toModel())
        return true
    }

    private fun onUp(view: View, event: MotionEvent): Boolean {
        val i = index ?: return false
        index = null
        if (dragging) {
            onDrop(i, map.latLngAt(event).toModel())
        } else {
            view.performClick()
            onTap(i)
        }
        return true
    }

    private fun onCancel(event: MotionEvent): Boolean {
        val i = index ?: return false
        index = null
        if (dragging) onDrop(i, map.latLngAt(event).toModel())
        return true
    }
}

/** A touch has become a drag once it travels further than the platform slop. */
internal fun exceedsSlop(down: Offset, now: Offset, slopPx: Float): Boolean =
    (now - down).getDistance() > slopPx

private fun MapLibreMap.latLngAt(event: MotionEvent) =
    projection.fromScreenLocation(PointF(event.x, event.y))

private fun org.maplibre.android.geometry.LatLng.toModel() = LatLng(latitude, longitude)
