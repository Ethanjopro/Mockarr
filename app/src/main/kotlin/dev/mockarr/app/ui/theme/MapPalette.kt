package dev.mockarr.app.ui.theme

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * Every colour the map layers, marker bitmaps, wait chips and route
 * thumbnails draw with — as ARGB ints, because they are consumed by MapLibre
 * property expressions and `android.graphics.Paint`, not Compose. Both theme
 * variants live in Theme.kt next to the Material scheme they derive from.
 *
 * Pure data with no Android calls so unit tests can construct it.
 */
@Immutable
data class MapPalette(
    val route: Int,
    val routeCasing: Int,
    /** The stretch already driven: the accent, spent. */
    val routeTravelled: Int,
    /** Soft halo under the route (the neon road at night); transparent by day. */
    val routeGlow: Int,
    val fallbackRoute: Int,
    val offRoad: Int,
    val position: Int,
    val positionRing: Int,
    val holdPin: Int,
    val stopStart: Int,
    val stopVia: Int,
    val stopEnd: Int,
    val stopText: Int,
    val stopRing: Int,
    val selection: Int,
    val chip: Int,
    val chipText: Int,
    val chipActive: Int,
    val chipActiveText: Int,
    /** Tint of MapLibre's attribution "i" (the only stock map control left on). */
    val attribution: Int,
) {
    companion object {
        /** MapLibre colour properties take CSS strings; keep this free of android.graphics. */
        fun css(argb: Int): String {
            val alpha = ((argb ushr ALPHA_SHIFT) and CHANNEL_MASK) / CHANNEL_MAX
            val red = (argb ushr RED_SHIFT) and CHANNEL_MASK
            val green = (argb ushr GREEN_SHIFT) and CHANNEL_MASK
            val blue = argb and CHANNEL_MASK
            // Locale.ROOT: a German or French device would otherwise write "1,000" and
            // MapLibre would reject every map colour (critique, session 40).
            return "rgba($red,$green,$blue,${String.format(Locale.ROOT, "%.3f", alpha)})"
        }

        private const val ALPHA_SHIFT = 24
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val CHANNEL_MASK = 0xFF
        private const val CHANNEL_MAX = 255f
    }
}
