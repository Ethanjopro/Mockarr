package dev.mockarr.app.ui.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
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
 * A 56dp pill filling its share of a row (DESIGN.md → Buttons → Pill): Pause /
 * Resume / Finish, the Start choice, and every dialog verb. [iconRes] is
 * optional — dialog verbs (Save, Set, Discard) carry none.
 */
@Composable
fun RowScope.ActionPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconRes: Int? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentDescription: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = pillModifier(contentDescription),
    ) {
        PillContent(label, iconRes)
    }
}

/** The pill's secondary form: transparent with the outline stroke — Cancel beside a dialog's verb. */
@Composable
fun RowScope.OutlinedActionPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconRes: Int? = null,
    contentDescription: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = pillModifier(contentDescription),
    ) {
        PillContent(label, iconRes)
    }
}

private fun RowScope.pillModifier(contentDescription: String?): Modifier {
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    return Modifier.weight(1f).height(Tokens.pillHeight).then(semantics)
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
