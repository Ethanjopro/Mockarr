package dev.mockarr.app.ui.map

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkerDragTest {

    @Test
    fun `a wobble inside the slop is still a tap`() {
        assertFalse(exceedsSlop(Offset(100f, 100f), Offset(104f, 103f), slopPx = 8f))
    }

    @Test
    fun `travel past the slop becomes a drag`() {
        assertTrue(exceedsSlop(Offset(100f, 100f), Offset(100f, 112f), slopPx = 8f))
    }

    private fun gate(draggable: Int? = null) = DragGate(slopPx = 8f).apply { draggableIndex = draggable }

    @Test
    fun `only the armed stop promotes to a drag`() {
        val armed = gate(draggable = 2)
        armed.start(hit = 2, at = Offset.Zero)

        assertEquals(2, armed.drag(Offset(20f, 0f)))
        assertTrue(armed.end()!!.dragged)
    }

    @Test
    fun `a swipe on an unarmed marker neither drags nor taps`() {
        val unarmed = gate(draggable = null)
        assertTrue(unarmed.start(hit = 1, at = Offset.Zero))

        assertNull(unarmed.drag(Offset(20f, 0f)))
        val end = unarmed.end()!!
        assertFalse(end.dragged)
        assertFalse(end.tap)
    }

    @Test
    fun `a still press on any marker is a tap regardless of Move mode`() {
        val unarmed = gate(draggable = null)
        unarmed.start(hit = 1, at = Offset.Zero)

        assertNull(unarmed.drag(Offset(3f, 3f)))
        assertEquals(DragGate.End(1, dragged = false, tap = true), unarmed.end())
    }

    @Test
    fun `clearing Move mid-drag does not abort the drag in flight`() {
        val armed = gate(draggable = 0)
        armed.start(hit = 0, at = Offset.Zero)
        armed.drag(Offset(20f, 0f))

        armed.draggableIndex = null

        assertEquals(0, armed.drag(Offset(40f, 0f)))
        assertTrue(armed.end()!!.dragged)
    }

    @Test
    fun `a miss owns nothing`() {
        val armed = gate(draggable = 1)

        assertFalse(armed.start(hit = null, at = Offset.Zero))
        assertFalse(armed.active)
        assertNull(armed.drag(Offset(20f, 0f)))
        assertNull(armed.end())
    }
}
