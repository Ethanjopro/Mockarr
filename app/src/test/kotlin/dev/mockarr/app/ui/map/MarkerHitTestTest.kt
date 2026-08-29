package dev.mockarr.app.ui.map

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkerHitTestTest {

    private val density = 2f
    private val disc = (WAYPOINT_RADIUS_DP + WAYPOINT_STROKE_DP) * density // 27 px

    @Test
    fun `hits inside the drawn disc and misses just outside it`() {
        val markers = listOf(Offset(100f, 100f))

        assertEquals(0, hitWaypoint(Offset(100f + disc, 100f), markers, null, density))
        assertNull(hitWaypoint(Offset(100f + disc + 1f, 100f), markers, null, density))
    }

    @Test
    fun `the selected stop grows its target`() {
        val markers = listOf(Offset(100f, 100f))
        val tap = Offset(100f + disc + SELECTED_GROW_DP * density, 100f)

        assertNull(hitWaypoint(tap, markers, null, density))
        assertEquals(0, hitWaypoint(tap, markers, 0, density))
    }

    @Test
    fun `overlaps resolve to the selected stop, else the higher index`() {
        val markers = listOf(Offset(100f, 100f), Offset(110f, 100f))
        val tap = Offset(105f, 100f)

        assertEquals(1, hitWaypoint(tap, markers, null, density))
        assertEquals(0, hitWaypoint(tap, markers, 0, density))
    }
}
