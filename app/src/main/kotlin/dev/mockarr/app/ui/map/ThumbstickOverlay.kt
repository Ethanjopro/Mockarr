package dev.mockarr.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.mockarr.app.R
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

// 20 Hz: at 5 Hz each tick was a visible 44 px hop (docs/research/thumbstick-rnd.md).
internal const val NUDGE_TICK_MILLIS = 50L

private const val BASE_SIZE_DP = 120
private const val KNOB_SIZE_DP = 44
private const val DISABLED_ALPHA = 0.38f
private const val BASE_ALPHA = 0.92f

private const val SCREEN_SPEED_PX_PER_SECOND = 180.0
private const val WEB_MERCATOR_MPP_EQUATOR_Z0 = 156_543.03
private const val MIN_NUDGE_MPS = 0.1
private const val MAX_NUDGE_MPS = 300.0
private const val FULL_CIRCLE_DEGREES = 360.0
private const val DEAD_ZONE = 0.08f
private const val RESPONSE_EXPONENT = 2.0

/**
 * Corner joystick that nudges the held mocked location. [onNudge] fires at
 * 20 Hz while the knob is deflected, with the stick bearing (0° = north) and
 * the raw deflection fraction 0..1 ([nudgeMeters] applies the response
 * curve). Visible but inert when [enabled] is false.
 */
@Composable
fun ThumbstickOverlay(
    enabled: Boolean,
    onNudge: (bearingDegrees: Double, deflection: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val maxRadiusPx = with(LocalDensity.current) { (BASE_SIZE_DP - KNOB_SIZE_DP).dp.toPx() / 2f }
    // The knob warms from indigo to the hold amber as the push strengthens,
    // so the user can see how hard they are pushing.
    val push = responseCurve((drag.getDistance() / maxRadiusPx).coerceIn(0f, 1f))
    val knobColor = lerp(MaterialTheme.colorScheme.primary, MockarrTheme.colors.hold, push)

    val description = stringResource(R.string.thumbstick_cd)
    LaunchedEffect(enabled) {
        if (!enabled) {
            drag = Offset.Zero
            dragging = false
        }
    }
    LaunchedEffect(dragging) {
        // Reads drag.value each pass — a coroutine loop sees fresh state.
        while (dragging) {
            val current = drag
            if (current != Offset.Zero) {
                val deflection = (current.getDistance() / maxRadiusPx).coerceIn(0f, 1f)
                onNudge(stickBearingDegrees(current.x, current.y), deflection)
            }
            delay(NUDGE_TICK_MILLIS)
        }
    }

    Box(
        modifier = modifier
            .size(BASE_SIZE_DP.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .shadow(Tokens.floatingElevation, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = BASE_ALPHA))
            .semantics { contentDescription = description }
            .pointerInput(enabled) {
                if (enabled) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            drag = Offset.Zero
                        },
                        onDragCancel = {
                            dragging = false
                            drag = Offset.Zero
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        drag = clampToRadius(drag + dragAmount, maxRadiusPx)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
                .size(KNOB_SIZE_DP.dp)
                .clip(CircleShape)
                .background(knobColor),
        )
    }
}

internal fun clampToRadius(offset: Offset, maxRadius: Float): Offset {
    val distance = offset.getDistance()
    return if (distance <= maxRadius || distance == 0f) {
        offset
    } else {
        offset * (maxRadius / distance)
    }
}

/** Stick vector → compass bearing: up = 0°, right = 90° (screen +y is south). */
internal fun stickBearingDegrees(x: Float, y: Float): Double {
    val degrees = Math.toDegrees(atan2(x.toDouble(), -y.toDouble()))
    return (degrees + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES
}

/**
 * Stick travel → speed fraction: an 8% dead zone (a resting thumb never
 * drifts), then a squared curve so the inner half of the travel is a fine
 * range (half stick ≈ 22% speed) while full deflection still hits full speed.
 */
internal fun responseCurve(deflection: Float): Float {
    val past = ((deflection - DEAD_ZONE) / (1f - DEAD_ZONE)).coerceIn(0f, 1f)
    return past.toDouble().pow(RESPONSE_EXPONENT).toFloat()
}

/**
 * Meters to move this tick. Speed follows [responseCurve] and is scaled by
 * meters-per-pixel at the camera zoom/latitude, so the dot crosses the screen
 * at the same apparent rate regardless of zoom. Zero inside the dead zone.
 */
internal fun nudgeMeters(
    deflection: Float,
    zoom: Double,
    latitudeDegrees: Double,
    dtSeconds: Double,
): Double {
    val fraction = responseCurve(deflection)
    if (fraction == 0f) return 0.0
    val metersPerPixel =
        WEB_MERCATOR_MPP_EQUATOR_Z0 * cos(Math.toRadians(latitudeDegrees)) / 2.0.pow(zoom)
    val metersPerSecond = (fraction * SCREEN_SPEED_PX_PER_SECOND * metersPerPixel)
        .coerceIn(MIN_NUDGE_MPS, MAX_NUDGE_MPS)
    return metersPerSecond * dtSeconds
}
