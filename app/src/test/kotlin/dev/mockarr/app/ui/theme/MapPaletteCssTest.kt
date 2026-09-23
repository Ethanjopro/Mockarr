package dev.mockarr.app.ui.theme

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MapPaletteCssTest {

    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun `css colours use a decimal point whatever the device language`() {
        // A German device wrote "rgba(57,73,171,0,502)" and MapLibre rejected every map colour.
        Locale.setDefault(Locale.GERMANY)
        assertEquals("rgba(57,73,171,0.502)", MapPalette.css(0x803949AB.toInt()))
    }

    @Test
    fun `an opaque colour reads alpha 1`() {
        assertEquals("rgba(255,255,255,1.000)", MapPalette.css(0xFFFFFFFF.toInt()))
    }
}
