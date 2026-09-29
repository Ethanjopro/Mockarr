package dev.mockarr.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * The app's one labelled button (DESIGN.md → Buttons): a 56dp full pill with a
 * bold one-line label and an optional leading glyph. Filled indigo is the one
 * verb that moves you forward on a surface; [OutlinedPill] is its alternative
 * or back-out, always to its left. There is no colour parameter on purpose:
 * no screen repaints a pill (no red, no amber, no black) — a destructive verb
 * says what it loses and carries the trash glyph instead.
 */
@Composable
fun Pill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconRes: Int? = null,
    contentDescription: String? = null,
    textSize: PillTextSize? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.pillModifier(contentDescription),
        contentPadding = PillPadding,
    ) {
        PillContent(label, iconRes, textSize)
    }
}

/** The pill's secondary form: transparent with the outline stroke and indigo ink. */
@Composable
fun OutlinedPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconRes: Int? = null,
    contentDescription: String? = null,
    textSize: PillTextSize? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.pillModifier(contentDescription),
        contentPadding = PillPadding,
    ) {
        PillContent(label, iconRes, textSize)
    }
}

/** A [Pill] taking an equal share of a two-up row: Resume, Start of route, every dialog verb. */
@Composable
fun RowScope.ActionPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconRes: Int? = null,
    contentDescription: String? = null,
    textSize: PillTextSize? = null,
) = Pill(label, onClick, Modifier.weight(1f), enabled, iconRes, contentDescription, textSize)

/** An [OutlinedPill] taking an equal share of a two-up row: Cancel, End drive, Held spot. */
@Composable
fun RowScope.OutlinedActionPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconRes: Int? = null,
    contentDescription: String? = null,
    textSize: PillTextSize? = null,
) = OutlinedPill(label, onClick, Modifier.weight(1f), enabled, iconRes, contentDescription, textSize)

/**
 * The compact button, for actions inside rows, lists, the search card, the band and
 * snackbars. There is no text-only button (Ethan, 2026-09-24: "it should be obvious when
 * something is clickable that it is a button"): this is an outlined pill, 40dp tall with
 * a 48dp touch target. By default it has the outline stroke and indigo ink. On a tinted
 * surface ([ink]: only that surface's own content colour, the band's or the snackbar's)
 * the outline and label take that ink, so it reads as a button on every tone.
 */
@Composable
fun SmallPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, ink: Color = Color.Unspecified) {
    val content = ink.takeOrElse { MaterialTheme.colorScheme.primary }
    val stroke = ink.takeOrElse { MaterialTheme.colorScheme.outline }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(Tokens.hairline, stroke),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
        contentPadding = PillPadding,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.pillModifier(contentDescription: String?): Modifier {
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    return height(Tokens.pillHeight).then(semantics)
}

/**
 * One text size for the pills of a row. Each pill reports the size auto-fit chose and all of
 * them use the smallest, so a long label shrinking to 12sp doesn't sit beside a 16sp one
 * (START FROM at large text; Ethan, 2026-09-29).
 */
@Stable
class PillTextSize internal constructor() {
    internal var cap by mutableStateOf(PILL_MAX_FONT)
}

/** A [PillTextSize] for one row; new [keys] (other labels) start over from full size. */
@Composable
fun rememberPillTextSize(vararg keys: Any?): PillTextSize {
    val fontScale = LocalDensity.current.fontScale
    return remember(fontScale, *keys) { PillTextSize() }
}

@Composable
private fun PillContent(label: String, iconRes: Int?, textSize: PillTextSize?) {
    if (iconRes != null) {
        Icon(painterResource(iconRes), contentDescription = null)
        Spacer(Modifier.width(Tokens.space2))
    }
    // One line always: at large font scales the label shrinks rather than wrapping. No soft
    // wrap: with it, a label too long even at the floor silently lost its last word
    // ("Discard changes" read "Discard"); now the cut shows as an ellipsis.
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = PILL_MIN_FONT, maxFontSize = textSize?.cap ?: PILL_MAX_FONT),
        onTextLayout = { result ->
            val size = result.layoutInput.style.fontSize
            if (textSize != null && size.isSp && size < textSize.cap) textSize.cap = size
        },
    )
}

/** 16dp sides, not M3's 24: a two-up dialog row left ~90dp for an iconed label. */
private val PillPadding = PaddingValues(horizontal = Tokens.space4)

private val PILL_MIN_FONT = 12.sp
private val PILL_MAX_FONT = 16.sp
