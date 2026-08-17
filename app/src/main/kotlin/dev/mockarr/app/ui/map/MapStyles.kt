package dev.mockarr.app.ui.map

import dev.mockarr.core.data.MockarrSettings

/**
 * Style URL the map (and route thumbnails) should actually load. The default
 * light style swaps to the bundled dark-recolored liberty asset in dark theme;
 * a user-configured custom URL is always respected verbatim.
 */
fun effectiveStyleUrl(configured: String, darkTheme: Boolean): String =
    if (darkTheme && configured == MockarrSettings.DEFAULT_TILE_STYLE_URL) {
        MockarrSettings.DEFAULT_TILE_STYLE_URL_DARK
    } else {
        configured
    }
