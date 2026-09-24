package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import dev.mockarr.app.R
import dev.mockarr.app.ui.theme.MapPopover
import dev.mockarr.app.ui.theme.SmallPill
import dev.mockarr.app.ui.theme.Tokens

/**
 * First-run coaching: a caret popover on the sheet handle saying the sheet
 * pulls up. Shown once; any dismissal (Got it, outside tap, Back) marks it seen.
 */
@Composable
internal fun SheetHintPopover(anchor: Offset, onDismiss: () -> Unit) {
    MapPopover(anchor = anchor, onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = Tokens.space4, vertical = Tokens.space3)) {
            Text(
                text = stringResource(R.string.hint_sheet_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(R.string.hint_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Tokens.space1),
            )
        }
        SmallPill(
            label = stringResource(R.string.hint_got_it),
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End).padding(end = Tokens.space3, bottom = Tokens.space2),
        )
    }
}
