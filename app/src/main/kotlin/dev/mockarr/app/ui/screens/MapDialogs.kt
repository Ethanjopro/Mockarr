package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import dev.mockarr.app.R
import dev.mockarr.app.ui.formatRouteTimestamp
import dev.mockarr.app.ui.theme.DialogAction
import dev.mockarr.app.ui.theme.MockarrDialog
import dev.mockarr.app.ui.theme.Tokens

private val WAIT_PRESET_MINUTES = listOf(1, 5, 15, 30)
private const val SECONDS_PER_MINUTE = 60

/** Picks a dwell duration: preset chips or a free custom-minutes field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WaypointWaitDialog(
    initialSeconds: Int,
    onConfirm: (seconds: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMinutes = initialSeconds / SECONDS_PER_MINUTE
    var selectedPreset by remember {
        mutableStateOf(initialMinutes.takeIf { it in WAIT_PRESET_MINUTES })
    }
    var customText by remember {
        val custom = initialMinutes.takeIf { it > 0 && it !in WAIT_PRESET_MINUTES }
        mutableStateOf(custom?.toString().orEmpty())
    }
    val customMinutes = customText.toIntOrNull()?.takeIf { it > 0 }
    val chosenMinutes = selectedPreset ?: customMinutes

    MockarrDialog(
        title = stringResource(R.string.dialog_wait_title),
        onDismissRequest = onDismiss,
        confirm = DialogAction(
            label = stringResource(R.string.dialog_set),
            onClick = { chosenMinutes?.let { onConfirm(it * SECONDS_PER_MINUTE) } },
            enabled = chosenMinutes != null,
        ),
        dismiss = DialogAction(stringResource(R.string.dialog_cancel), onDismiss),
    ) {
        Text(
            text = stringResource(R.string.dialog_wait_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Tokens.space2))
        // FlowRow: the dialog is too narrow for four chips — let the
        // last chip wrap as a whole instead of shredding its label.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.space2)) {
            WAIT_PRESET_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = selectedPreset == minutes,
                    onClick = {
                        selectedPreset = minutes
                        customText = ""
                    },
                    label = { Text(stringResource(R.string.dialog_wait_minutes, minutes), softWrap = false) },
                )
            }
        }
        Spacer(Modifier.height(Tokens.space2))
        OutlinedTextField(
            value = customText,
            onValueChange = {
                customText = it
                selectedPreset = null
            },
            label = { Text(stringResource(R.string.dialog_wait_custom)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
    }
}

@Composable
internal fun RouteFromHoldDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MockarrDialog(
        title = stringResource(R.string.dialog_route_from_hold_title),
        text = stringResource(R.string.dialog_route_from_hold_body),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(R.string.dialog_route_and_play), onConfirm),
        dismiss = DialogAction(stringResource(R.string.dialog_cancel), onDismiss),
    )
}

@Composable
internal fun SaveRouteDialog(
    suggestedName: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // The host resolved (or gave up on) the place name before opening us, so
    // the field never changes under the user's cursor.
    val defaultName = remember { "Route " + formatRouteTimestamp(System.currentTimeMillis()) }
    var name by remember { mutableStateOf(suggestedName ?: defaultName) }
    MockarrDialog(
        title = stringResource(R.string.dialog_save_title),
        onDismissRequest = onDismiss,
        confirm = DialogAction(
            label = stringResource(R.string.dialog_save),
            onClick = { onConfirm(name.trim()) },
            enabled = name.isNotBlank(),
        ),
        dismiss = DialogAction(stringResource(R.string.dialog_cancel), onDismiss),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.dialog_save_name)) },
            singleLine = true,
        )
    }
}

/** "1", "0.5", "0.25", "2" — no trailing zeros, no scientific notation. */
internal fun formatMultiplier(multiplier: Double): String =
    if (multiplier == multiplier.toLong().toDouble()) {
        multiplier.toLong().toString()
    } else {
        multiplier.toString().trimEnd('0')
    }
