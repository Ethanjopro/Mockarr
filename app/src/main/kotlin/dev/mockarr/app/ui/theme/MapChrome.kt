package dev.mockarr.app.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * Strava's floating map control: a white circle with a soft drop shadow and an
 * on-surface glyph. Every control that floats over the map (tools row, FAB
 * stack) is one of these, so the map chrome reads as a single family.
 */
@Composable
fun MapPill(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (selected) scheme.primary else scheme.surfaceContainerLowest
    val ink = when {
        selected -> scheme.onPrimary
        enabled -> scheme.onSurface
        else -> scheme.onSurface.copy(alpha = DISABLED_ALPHA)
    }
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = container,
        contentColor = ink,
        shadowElevation = Tokens.floatingElevation,
        modifier = modifier.size(Tokens.pillSize).then(semantics),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Convenience for the common icon-only pill. */
@Composable
fun MapIconPill(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    MapPill(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
        selected = selected,
    ) {
        Icon(painter, contentDescription = null)
    }
}

/**
 * Strava's builder menu: a white card with a caret that points at [anchor]
 * (window pixels). Sits above the anchor and flips below when there is no
 * room; the caret stays on the anchor either way. [modal] decides whether
 * outside touches dismiss it or pass through to the map.
 */
@Composable
fun MapPopover(
    anchor: Offset,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    modal: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { POPOVER_GAP.roundToPx() }
    val caretPx = with(density) { CARET_SIZE.roundToPx() }
    val marginPx = with(density) { Tokens.mapEdge.roundToPx() }
    var placement by remember { mutableStateOf(PopoverPlacement(above = true, caretX = 0)) }
    val provider = remember(anchor, gapPx, marginPx) {
        AnchorPositionProvider(anchor, gapPx, marginPx) { placement = it }
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        // Modal (a menu): outside taps and Back dismiss and go no further.
        // Non-modal (a marker callout): touches reach the map, so the popover
        // rides the marker through a pan and the host dismisses on map tap/Back.
        properties = PopupProperties(focusable = modal, dismissOnClickOutside = modal),
    ) {
        val cardColor = MaterialTheme.colorScheme.surfaceContainerLowest
        Column(modifier = modifier.widthIn(min = POPOVER_MIN_WIDTH, max = POPOVER_MAX_WIDTH)) {
            if (!placement.above) Caret(cardColor, placement.caretX, caretPx, pointsUp = true)
            Surface(
                shape = Tokens.cardShape,
                color = cardColor,
                shadowElevation = Tokens.popoverElevation,
            ) {
                Column(content = content)
            }
            if (placement.above) Caret(cardColor, placement.caretX, caretPx, pointsUp = false)
        }
    }
}

/** One popover row: label left, glyph right; destructive rows use the error role. */
@Composable
fun PopoverRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    divider: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val ink = when {
        !enabled -> scheme.onSurface.copy(alpha = DISABLED_ALPHA)
        destructive -> scheme.error
        else -> scheme.onSurface
    }
    if (divider) HorizontalDivider(color = scheme.outlineVariant)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Tokens.inset, vertical = Tokens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = ink,
            modifier = Modifier.weight(1f),
        )
        if (icon != null) {
            Spacer(Modifier.width(Tokens.space4))
            Icon(icon, contentDescription = null, tint = ink)
        }
    }
}

@Composable
private fun Caret(color: Color, caretX: Int, caretPx: Int, pointsUp: Boolean) {
    val half = caretPx / 2
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset(caretX - half, 0) }
            .size(with(density) { caretPx.toDp() }, with(density) { half.toDp() })
            .drawBehind { drawPath(caretPath(size, pointsUp), color) },
    )
}

private fun caretPath(size: Size, pointsUp: Boolean): Path = Path().apply {
    if (pointsUp) {
        moveTo(0f, size.height)
        lineTo(size.width / 2f, 0f)
        lineTo(size.width, size.height)
    } else {
        moveTo(0f, 0f)
        lineTo(size.width / 2f, size.height)
        lineTo(size.width, 0f)
    }
    close()
}

private data class PopoverPlacement(val above: Boolean, val caretX: Int)

/** Centres the popup on the anchor, above it when it fits, clamped to the window. */
private class AnchorPositionProvider(
    private val anchor: Offset,
    private val gapPx: Int,
    private val marginPx: Int,
    private val onPlaced: (PopoverPlacement) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val anchorX = anchor.x.roundToInt()
        val anchorY = anchor.y.roundToInt()
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = (anchorX - popupContentSize.width / 2).coerceIn(marginPx, maxX)
        val above = anchorY - gapPx - popupContentSize.height >= marginPx
        val y = if (above) anchorY - gapPx - popupContentSize.height else anchorY + gapPx
        onPlaced(PopoverPlacement(above = above, caretX = anchorX - x))
        return IntOffset(x, y)
    }
}

private const val DISABLED_ALPHA = 0.38f
private val POPOVER_GAP = 14.dp
private val CARET_SIZE = 16.dp
private val POPOVER_MIN_WIDTH = 220.dp
private val POPOVER_MAX_WIDTH = 320.dp
private val ROW_MIN_HEIGHT = 52.dp
