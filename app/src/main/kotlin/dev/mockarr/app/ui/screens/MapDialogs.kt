package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.mockarr.app.ui.formatDurationShort

private val WAIT_PRESET_MINUTES = listOf(1, 5, 15, 30)
private const val SECONDS_PER_MINUTE = 60

/**
 * Non-blocking action card for a tapped waypoint marker, slotted into the
 * bottom stack so the map stays interactive behind it. Wait-time actions are
 * not offered on the destination — the "Stay at destination" setting covers
 * post-arrival holds.
 */
@Composable
internal fun WaypointOptionsCard(
    stopNumber: Int,
    isDestination: Boolean,
    currentWaitSeconds: Int,
    onSetWait: () -> Unit,
    onClearWait: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Stop $stopNumber", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (currentWaitSeconds > 0) {
                        "Waits ${formatDurationShort(currentWaitSeconds.toDouble())} here"
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close stop options")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isDestination) {
                    val waitLabel = if (currentWaitSeconds > 0) "Edit wait" else "Set wait"
                    TextButton(onClick = onSetWait) { Text(waitLabel) }
                    if (currentWaitSeconds > 0) {
                        TextButton(onClick = onClearWait) { Text("Remove wait") }
                    }
                }
                TextButton(onClick = onDelete) { Text("Remove stop") }
            }
        }
    }
}

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
        title = { Text("Wait at this stop") },
        text = {
            Column {
                Text("Playback will pause here before continuing.")
                Spacer(Modifier.height(8.dp))
                // FlowRow: the dialog is too narrow for four chips — let the
                // last chip wrap as a whole instead of shredding its label.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WAIT_PRESET_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = selectedPreset == minutes,
                            onClick = {
                                selectedPreset = minutes
                                customText = ""
                            },
                            label = { Text("$minutes min", softWrap = false) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = {
                        customText = it
                        selectedPreset = null
                    },
                    label = { Text("Custom minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosenMinutes != null,
                onClick = { chosenMinutes?.let { onConfirm(it * SECONDS_PER_MINUTE) } },
            ) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Play pressed while holding elsewhere: pick where the drive starts. */
@Composable
internal fun StartChoiceDialog(
    onStartFromHold: () -> Unit,
    onPlayAsBuilt: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start from held location?") },
        text = {
            Text("Your location is currently held somewhere else. Where should this drive start?")
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onStartFromHold) { Text("Start from held location") }
                TextButton(onClick = onPlayAsBuilt) { Text("Play route as built") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
