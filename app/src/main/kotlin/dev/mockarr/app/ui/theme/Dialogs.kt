package dev.mockarr.app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties

/**
 * The app's dialog: the stat card floated to the centre — its 16dp lowest
 * surface at popover elevation, the card's `mapEdge` side margins (capped at
 * [Tokens.dialogMaxWidth] on tablets), a bold title, free content, then the
 * sheet's two-up 56dp pill row: outlined Cancel beside the filled verb (DESIGN.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockarrDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirm: DialogAction,
    modifier: Modifier = Modifier,
    dismiss: DialogAction? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        // Not the platform's dialog width: the card's own margins, like every other floating object.
        modifier = modifier.padding(horizontal = Tokens.mapEdge).widthIn(max = Tokens.dialogMaxWidth),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = Tokens.cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = Tokens.popoverElevation,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(Tokens.inset)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Tokens.space3))
                content()
                Spacer(Modifier.height(Tokens.space6))
                DialogActions(confirm, dismiss)
            }
        }
    }
}

/** Text-only body. */
@Composable
fun MockarrDialog(
    title: String,
    text: String,
    onDismissRequest: () -> Unit,
    confirm: DialogAction,
    dismiss: DialogAction? = null,
) {
    MockarrDialog(title = title, onDismissRequest = onDismissRequest, confirm = confirm, dismiss = dismiss) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Resume / Finish, Held spot / Route start — and Cancel / Discard: the same equal-weight pill row. */
@Composable
private fun DialogActions(confirm: DialogAction, dismiss: DialogAction?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
    ) {
        if (dismiss != null) {
            OutlinedActionPill(label = dismiss.label, enabled = dismiss.enabled, onClick = dismiss.onClick)
        }
        val colors = if (confirm.destructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        }
        ActionPill(label = confirm.label, enabled = confirm.enabled, onClick = confirm.onClick, colors = colors)
    }
}
