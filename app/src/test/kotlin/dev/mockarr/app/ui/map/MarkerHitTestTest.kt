package dev.mockarr.app.ui.map

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkerHitTestTest {

    private val density = 2f
    private val disc = (WAYPOINT_RADIUS_DP + WAYPOINT_STROKE_DP) * density // 27 px

    private val reach = WAYPOINT_TOUCH_RADIUS_DP * density // 48 px: a 48dp target

    @Test
    fun `a finger-sized target around the disc, and nothing past it`() {
        val markers = listOf(Offset(100f, 100f))

        assertEquals(0, hitWaypoint(Offset(100f + disc, 100f), markers, null, density))
        assertEquals(0, hitWaypoint(Offset(100f + reach, 100f), markers, null, density))
        assertNull(hitWaypoint(Offset(100f + reach + 1f, 100f), markers, null, density))
    }

    @Test
    fun `a tap on a drawn disc beats a nearer stop it misses`() {
        // Stop 0 is selected (grown disc, 31 px). The tap sits on it at 30 px, while stop 1's
        // centre is nearer (29 px) but its 27 px disc misses the tap.
        val markers = listOf(Offset(100f, 100f), Offset(159f, 100f))
        val tap = Offset(130f, 100f)

        assertEquals(0, hitWaypoint(tap, markers, 0, density))
        assertEquals(1, hitWaypoint(tap, markers, null, density))
    }

    @Test
    fun `off every disc the nearest stop within reach wins`() {
        val markers = listOf(Offset(100f, 100f), Offset(200f, 100f))

        assertEquals(0, hitWaypoint(Offset(140f, 100f), markers, null, density))
        assertEquals(1, hitWaypoint(Offset(160f, 100f), markers, null, density))
    }

    @Test
    fun `overlaps resolve to the selected stop, else the higher index`() {
        val markers = listOf(Offset(100f, 100f), Offset(110f, 100f))
        val tap = Offset(105f, 100f)

        assertEquals(1, hitWaypoint(tap, markers, null, density))
        assertEquals(0, hitWaypoint(tap, markers, 0, density))
    }
}
