package dev.mockarr.app.ui.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.pillModifier(contentDescription),
    ) {
        PillContent(label, iconRes)
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
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.pillModifier(contentDescription),
    ) {
        PillContent(label, iconRes)
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
) = Pill(label, onClick, Modifier.weight(1f), enabled, iconRes, contentDescription)

/** An [OutlinedPill] taking an equal share of a two-up row: Cancel, End drive, Held spot. */
@Composable
fun RowScope.OutlinedActionPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconRes: Int? = null,
    contentDescription: String? = null,
) = OutlinedPill(label, onClick, Modifier.weight(1f), enabled, iconRes, contentDescription)

private fun Modifier.pillModifier(contentDescription: String?): Modifier {
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    return height(Tokens.pillHeight).then(semantics)
}

@Composable
private fun PillContent(label: String, iconRes: Int?) {
    if (iconRes != null) {
        Icon(painterResource(iconRes), contentDescription = null)
        Spacer(Modifier.width(Tokens.space2))
    }
    // One line always: at large font scales the label shrinks rather than wrapping.
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = PILL_MIN_FONT, maxFontSize = PILL_MAX_FONT),
    )
}

private val PILL_MIN_FONT = 12.sp
private val PILL_MAX_FONT = 16.sp
