package dev.mockarr.app.ui.map

import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.geometry.Offset
import dev.mockarr.core.model.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Owns any touch that starts on a stop marker: a tap selects it, and — only
 * for the stop the popover's Move armed — a drag past the touch slop moves it
 * (the map never sees the gesture, so it does not pan). Everything else falls
 * through to MapLibre untouched.
 */
internal class MarkerDragHandler(
    private val map: MapLibreMap,
    private val hitTest: (org.maplibre.android.geometry.LatLng) -> Int?,
    slopPx: Float,
    private val onTap: (Int) -> Unit,
    private val onDrag: (Int, LatLng) -> Unit,
    private val onDrop: (Int, LatLng) -> Unit,
) : View.OnTouchListener {

    /** Off while playing: markers are inert and the map keeps every gesture. */
    var enabled: Boolean = true

    /** The one stop Move mode allows to drag; null = no marker drags. */
    var draggableIndex: Int?
        get() = gate.draggableIndex
        set(value) {
            gate.draggableIndex = value
        }

    private val gate = DragGate(slopPx)

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

    private fun onDown(event: MotionEvent): Boolean =
        gate.start(if (enabled) hitTest(map.latLngAt(event)) else null, Offset(event.x, event.y))

    private fun onMove(event: MotionEvent): Boolean {
        if (!gate.active) return false
        gate.drag(Offset(event.x, event.y))?.let { onDrag(it, map.latLngAt(event).toModel()) }
        return true
    }

    private fun onUp(view: View, event: MotionEvent): Boolean {
        val end = gate.end() ?: return false
        if (end.dragged) {
            onDrop(end.index, map.latLngAt(event).toModel())
        } else if (end.tap) {
            view.performClick()
            onTap(end.index)
        }
        return true
    }

    private fun onCancel(event: MotionEvent): Boolean {
        val end = gate.end() ?: return false
        if (end.dragged) onDrop(end.index, map.latLngAt(event).toModel())
        return true
    }
}

/**
 * Pure bookkeeping for a marker touch, free of Android types so the Move-mode
 * gating is unit-testable: any marker hit owns the gesture (tap = select), but
 * only [draggableIndex] — the stop the popover's Move armed — promotes to a
 * drag past the slop. A swipe on any other marker stays owned but dead
 * (handing MapLibre a mid-stream MOVE it never saw the DOWN for would not pan
 * anyway), and its release is not a tap.
 */
internal class DragGate(private val slopPx: Float) {

    /** The one stop Move mode allows to drag; null = no marker drags. */
    var draggableIndex: Int? = null

    /** True from a marker-hit DOWN until the gesture ends. */
    val active: Boolean get() = index != null

    private var index: Int? = null
    private var down = Offset.Zero
    private var dragging = false
    private var moved = false

    /** Starts a gesture; true when [hit] is a marker and the gate owns it. */
    fun start(hit: Int?, at: Offset): Boolean {
        index = hit
        down = at
        dragging = false
        moved = false
        return hit != null
    }

    /** The stop to drag this frame, or null (not past slop, or not the armed stop). */
    fun drag(at: Offset): Int? {
        val i = index ?: return null
        if (exceedsSlop(down, at, slopPx)) {
            moved = true
            // The dragging latch comes first so clearing Move mid-drag can't abort it.
            if (!dragging && i == draggableIndex) dragging = true
        }
        return if (dragging) i else null
    }

    /** Ends the gesture: a drop for a drag, a tap for a still press, dead otherwise. */
    fun end(): End? {
        val i = index ?: return null
        index = null
        return End(i, dragged = dragging, tap = !dragging && !moved)
    }

    /** How a marker gesture finished. */
    data class End(val index: Int, val dragged: Boolean, val tap: Boolean)
}

/** A touch has become a drag once it travels further than the platform slop. */
internal fun exceedsSlop(down: Offset, now: Offset, slopPx: Float): Boolean =
    (now - down).getDistance() > slopPx

private fun MapLibreMap.latLngAt(event: MotionEvent) =
    projection.fromScreenLocation(PointF(event.x, event.y))

private fun org.maplibre.android.geometry.LatLng.toModel() = LatLng(latitude, longitude)
