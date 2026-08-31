package dev.mockarr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand indigo on every API level. Dynamic colour is deliberately OFF
 * (design brief, 2026-08-28): the map palette derives from these roles and a
 * wallpaper-driven accent would fight the route line and markers.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF0A1B6B),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF1E6B4E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA6F2CD),
    onTertiaryContainer = Color(0xFF002114),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE2E1E9),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = Color(0xFFB9C3FF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF0A1B6B),
    primaryContainer = Color(0xFF2A3A9F),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFF8BD6B2),
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF0F4F38),
    onTertiaryContainer = Color(0xFFA6F2CD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121319),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121319),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A30),
    surfaceContainerHighest = Color(0xFF34343B),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    inverseSurface = Color(0xFFE3E1E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF3949AB),
)

/**
 * Semantic roles Material's scheme doesn't name. State colour is separate from
 * the indigo accent: *ready* says "mocking works", *hold* says "you're parked
 * somewhere" (also the wait badge/chip colour). Each comes with an `on`
 * colour and a container pair for strips and banners.
 */
@Immutable
data class MockarrColors(
    val ready: Color,
    val onReady: Color,
    val readyContainer: Color,
    val onReadyContainer: Color,
    val hold: Color,
    val onHold: Color,
    val holdContainer: Color,
    val onHoldContainer: Color,
    val map: MapPalette,
)

private val LightExtras = MockarrColors(
    ready = Color(0xFF1E6B4E),
    onReady = Color(0xFFFFFFFF),
    readyContainer = Color(0xFFA6F2CD),
    onReadyContainer = Color(0xFF002114),
    hold = Color(0xFF8A5A00),
    onHold = Color(0xFFFFFFFF),
    holdContainer = Color(0xFFFFDEA8),
    onHoldContainer = Color(0xFF2B1A00),
    map = MapPalette(
        route = 0xFF3949AB.toInt(),
        routeCasing = 0xFFFFFFFF.toInt(),
        fallbackRoute = 0xFFB8741A.toInt(),
        offRoad = 0xFF3949AB.toInt(),
        position = 0xFF3949AB.toInt(),
        positionRing = 0xFFFFFFFF.toInt(),
        holdPin = 0xFFE0901E.toInt(),
        stopStart = 0xFF1E8A5A.toInt(),
        stopVia = 0xFF3949AB.toInt(),
        stopEnd = 0xFF1A1B21.toInt(),
        stopText = 0xFFFFFFFF.toInt(),
        stopRing = 0xFFFFFFFF.toInt(),
        selection = 0xFF5C6BC0.toInt(),
        chip = 0xF21A1B21.toInt(),
        chipText = 0xFFFFFFFF.toInt(),
        chipActive = 0xFFF0A422.toInt(),
        chipActiveText = 0xFF2B1A00.toInt(),
    ),
)

private val DarkExtras = MockarrColors(
    ready = Color(0xFF8BD6B2),
    onReady = Color(0xFF003824),
    readyContainer = Color(0xFF0F4F38),
    onReadyContainer = Color(0xFFA6F2CD),
    hold = Color(0xFFFFBB58),
    onHold = Color(0xFF442B00),
    holdContainer = Color(0xFF5F4100),
    onHoldContainer = Color(0xFFFFDEA8),
    map = MapPalette(
        route = 0xFF9FA8FF.toInt(),
        routeCasing = 0xFF121319.toInt(),
        fallbackRoute = 0xFFF0A422.toInt(),
        offRoad = 0xFF9FA8FF.toInt(),
        position = 0xFFB9C3FF.toInt(),
        positionRing = 0xFF121319.toInt(),
        holdPin = 0xFFFFBB58.toInt(),
        stopStart = 0xFF6FD3A4.toInt(),
        stopVia = 0xFF7A88E6.toInt(),
        stopEnd = 0xFFE3E1E9.toInt(),
        stopText = 0xFF121319.toInt(),
        stopRing = 0xFF121319.toInt(),
        selection = 0xFFDEE0FF.toInt(),
        chip = 0xF2E3E1E9.toInt(),
        chipText = 0xFF121319.toInt(),
        chipActive = 0xFFFFBB58.toInt(),
        chipActiveText = 0xFF442B00.toInt(),
    ),
)

val LocalMockarrColors = staticCompositionLocalOf { LightExtras }

/** Accessor mirroring `MaterialTheme.colorScheme` for the extra roles. */
object MockarrTheme {
    val colors: MockarrColors
        @Composable get() = LocalMockarrColors.current
}

@Composable
fun MockarrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extras = if (darkTheme) DarkExtras else LightExtras
    CompositionLocalProvider(LocalMockarrColors provides extras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            content = content,
        )
    }
}
