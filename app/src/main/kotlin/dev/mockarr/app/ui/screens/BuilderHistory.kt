package dev.mockarr.app.ui.screens

import dev.mockarr.core.model.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Undo/redo snapshots of the route maker's stop list. Every edit pushes the
 * list *before* the change; a drag pushes once at its first frame, so a whole
 * gesture undoes in one step. Pure — no Android, no coroutines beyond flows.
 */
class BuilderHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val undoStack = ArrayDeque<List<Waypoint>>()
    private val redoStack = ArrayDeque<List<Waypoint>>()
    private var dragging = false

    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /** Snapshot [before] an edit; any redo branch is discarded. */
    fun push(before: List<Waypoint>) {
        undoStack.addLast(before)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
        publish()
    }

    /** First unsettled drag frame pushes once; later frames are part of the same gesture. */
    fun beginDrag(before: List<Waypoint>): Boolean {
        if (dragging) return false
        dragging = true
        push(before)
        return true
    }

    fun endDrag() {
        dragging = false
    }

    /** Pops the last snapshot, parking [current] for redo; null when there is nothing to undo. */
    fun undo(current: List<Waypoint>): List<Waypoint>? {
        val restored = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        publish()
        return restored
    }

    /** Re-applies the last undone snapshot, parking [current] for undo. */
    fun redo(current: List<Waypoint>): List<Waypoint>? {
        val restored = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        publish()
        return restored
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        dragging = false
        publish()
    }

    private fun publish() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}

/** True when only waits differ: the route can be patched instead of refetched. */
internal fun List<Waypoint>.samePlaces(other: List<Waypoint>): Boolean =
    size == other.size && zip(other).all { (a, b) -> a.position == b.position }
