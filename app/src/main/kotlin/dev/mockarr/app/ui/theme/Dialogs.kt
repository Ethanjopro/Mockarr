package dev.mockarr.app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * The app's dialog: the stat card's 16dp lowest surface floating at popover
 * elevation, a bold title, free content, then a filled primary beside an
 * outlined Cancel — the same family as every other floating object (DESIGN.md).
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
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier) {
        Surface(
            shape = Tokens.cardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = Tokens.popoverElevation,
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

@Composable
private fun DialogActions(confirm: DialogAction, dismiss: DialogAction?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.space2, Alignment.End),
    ) {
        if (dismiss != null) {
            OutlinedButton(onClick = dismiss.onClick, enabled = dismiss.enabled) { Text(dismiss.label) }
        }
        val colors = if (confirm.destructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        }
        Button(onClick = confirm.onClick, enabled = confirm.enabled, colors = colors) { Text(confirm.label) }
    }
}
