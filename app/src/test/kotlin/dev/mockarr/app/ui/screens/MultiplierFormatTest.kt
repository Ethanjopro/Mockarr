package dev.mockarr.app.ui.screens

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiplierFormatTest {

    @Test
    fun `whole multipliers carry no decimals`() {
        assertEquals("1", formatMultiplier(1.0, Locale.US))
        assertEquals("4", formatMultiplier(4.0, Locale.US))
    }

    @Test
    fun `fractions drop trailing zeros`() {
        assertEquals("0.5", formatMultiplier(0.5, Locale.US))
        assertEquals("0.25", formatMultiplier(0.25, Locale.US))
    }

    @Test
    fun `fractions use the device's decimal separator`() {
        assertEquals("0,25", formatMultiplier(0.25, Locale.GERMANY))
    }
}
