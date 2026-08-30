package dev.mockarr.app.ui.screens

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapInteractionTest {

    private val interaction = MapInteraction(isStop = { it in 0..2 })

    @Test
    fun `marker tap selects with the popover shown`() {
        interaction.select(1)
        assertEquals(1, interaction.selectedWaypoint.value)
        assertEquals(1, interaction.popoverWaypoint.value)
    }

    @Test
    fun `sheet pick selects with the popover hidden`() {
        interaction.select(1, showPopover = false)
        assertEquals(1, interaction.selectedWaypoint.value)
        assertNull(interaction.popoverWaypoint.value)
    }

    @Test
    fun `a later marker tap shows the popover again`() {
        interaction.select(1, showPopover = false)
        interaction.select(1)
        assertEquals(1, interaction.popoverWaypoint.value)
    }

    @Test
    fun `deselecting clears the popover and the anchor`() {
        interaction.select(2, showPopover = false)
        interaction.setMarkerScreen(Offset(10f, 20f))
        interaction.select(null)
        assertNull(interaction.selectedWaypoint.value)
        assertNull(interaction.selectedMarkerScreen.value)
        assertNull(interaction.popoverWaypoint.value)
    }

    @Test
    fun `reset drops selection, move and popover`() {
        interaction.select(0, showPopover = false)
        interaction.beginMove(0)
        interaction.reset()
        assertNull(interaction.selectedWaypoint.value)
        assertNull(interaction.movingWaypoint.value)
        assertNull(interaction.popoverWaypoint.value)
    }
}
