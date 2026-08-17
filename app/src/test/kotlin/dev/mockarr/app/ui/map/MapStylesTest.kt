package dev.mockarr.app.ui.map

import dev.mockarr.core.data.MockarrSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class MapStylesTest {

    @Test
    fun `default style swaps to dark in dark theme`() {
        assertEquals(
            MockarrSettings.DEFAULT_TILE_STYLE_URL_DARK,
            effectiveStyleUrl(MockarrSettings.DEFAULT_TILE_STYLE_URL, darkTheme = true),
        )
    }

    @Test
    fun `default style stays light in light theme`() {
        assertEquals(
            MockarrSettings.DEFAULT_TILE_STYLE_URL,
            effectiveStyleUrl(MockarrSettings.DEFAULT_TILE_STYLE_URL, darkTheme = false),
        )
    }

    @Test
    fun `custom style url is respected in both themes`() {
        val custom = "https://example.com/style.json"

        assertEquals(custom, effectiveStyleUrl(custom, darkTheme = true))
        assertEquals(custom, effectiveStyleUrl(custom, darkTheme = false))
    }
}
