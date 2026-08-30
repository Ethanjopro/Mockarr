package dev.mockarr.app.ui.screens

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Waypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuilderHistoryTest {

    private fun stops(vararg lats: Double) = lats.map { Waypoint(LatLng(it, 0.0)) }

    @Test
    fun `undo restores the pushed snapshot`() {
        val history = BuilderHistory()
        history.push(stops(1.0))
        assertTrue(history.canUndo.value)
        assertEquals(stops(1.0), history.undo(stops(1.0, 2.0)))
        assertFalse(history.canUndo.value)
        assertTrue(history.canRedo.value)
    }

    @Test
    fun `redo re-applies what undo removed`() {
        val history = BuilderHistory()
        history.push(stops(1.0))
        history.undo(stops(1.0, 2.0))
        assertEquals(stops(1.0, 2.0), history.redo(stops(1.0)))
        assertTrue(history.canUndo.value)
        assertFalse(history.canRedo.value)
    }

    @Test
    fun `a push after undo clears the redo branch`() {
        val history = BuilderHistory()
        history.push(stops(1.0))
        history.undo(stops(1.0, 2.0))
        history.push(stops(1.0))
        assertFalse(history.canRedo.value)
        assertNull(history.redo(stops(1.0, 3.0)))
    }

    @Test
    fun `drag frames coalesce into one entry`() {
        val history = BuilderHistory()
        assertTrue(history.beginDrag(stops(1.0)))
        assertFalse(history.beginDrag(stops(1.5)))
        history.endDrag()
        assertEquals(stops(1.0), history.undo(stops(2.0)))
        assertFalse(history.canUndo.value)
    }

    @Test
    fun `the limit trims the oldest snapshot`() {
        val history = BuilderHistory(limit = 2)
        history.push(stops(1.0))
        history.push(stops(2.0))
        history.push(stops(3.0))
        assertEquals(stops(3.0), history.undo(stops(4.0)))
        assertEquals(stops(2.0), history.undo(stops(3.0)))
        assertNull(history.undo(stops(2.0)))
    }

    @Test
    fun `clear empties both stacks`() {
        val history = BuilderHistory()
        history.push(stops(1.0))
        history.undo(stops(1.0, 2.0))
        history.clear()
        assertFalse(history.canUndo.value)
        assertFalse(history.canRedo.value)
    }

    @Test
    fun `samePlaces is true for wait-only differences`() {
        val a = stops(1.0, 2.0)
        val b = listOf(a[0], a[1].copy(waitSeconds = 60))
        assertTrue(a.samePlaces(b))
        assertFalse(a.samePlaces(stops(1.0, 2.5)))
        assertFalse(a.samePlaces(stops(1.0)))
    }
}
