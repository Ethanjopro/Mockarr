package dev.mockarr.app.ui.map

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertFalse
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
}
