package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import dev.mockarr.app.R
import dev.mockarr.app.ui.formatRouteTimestamp
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_wait_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_wait_body))
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
        },
        confirmButton = {
            TextButton(
                enabled = chosenMinutes != null,
                onClick = { chosenMinutes?.let { onConfirm(it * SECONDS_PER_MINUTE) } },
            ) { Text(stringResource(R.string.dialog_set)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

@Composable
internal fun RouteFromHoldDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_route_from_hold_title)) },
        text = { Text(stringResource(R.string.dialog_route_from_hold_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.dialog_route_and_play)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

@Composable
internal fun SaveRouteDialog(
    suggestedName: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultName = remember { "Route " + formatRouteTimestamp(System.currentTimeMillis()) }
    var name by remember { mutableStateOf(suggestedName ?: defaultName) }
    var edited by remember { mutableStateOf(false) }
    // The reverse-geocoded suggestion may arrive after the dialog opens; adopt
    // it only while the user hasn't typed anything.
    LaunchedEffect(suggestedName) {
        if (!edited && suggestedName != null) name = suggestedName
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    edited = true
                },
                label = { Text(stringResource(R.string.dialog_save_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }) { Text(stringResource(R.string.dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/** "1", "0.5", "0.25", "2" — no trailing zeros, no scientific notation. */
internal fun formatMultiplier(multiplier: Double): String =
    if (multiplier == multiplier.toLong().toDouble()) {
        multiplier.toLong().toString()
    } else {
        multiplier.toString().trimEnd('0')
    }
