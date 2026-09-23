package dev.mockarr.app.ui.screens

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * A short window (a phone on its side) restructures instead of stretching: the
 * search field, card and sheet move into a start-side panel, and the map to its
 * right stays clear for the drive, the held pin and the thumbstick — before, a
 * centred sheet covered the lower half (adapt, session 41). Null on tall windows
 * (every portrait phone, tablets), where the bottom sheet already fits.
 */
@Composable
internal fun rememberSidePanelWidth(): Dp? {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val short = !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val wide = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    if (!short || !wide) return null
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp
    return minOf(SIDE_PANEL_MAX_WIDTH, windowWidth * SIDE_PANEL_FRACTION)
}

private val SIDE_PANEL_MAX_WIDTH = 420.dp
private const val SIDE_PANEL_FRACTION = 0.5f
