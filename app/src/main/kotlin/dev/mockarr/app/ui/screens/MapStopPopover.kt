package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import dev.mockarr.app.R
import dev.mockarr.app.ui.formatDurationShort
import dev.mockarr.app.ui.theme.MapPopover
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.PopoverRow
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.Waypoint

/**
 * Strava's tap-a-point callout, over the marker: the stop's name and wait,
 * then Wait (or a greyed "Stays at destination" on the last stop while that option is on)
 * · Move · Delete. [anchor] is the marker's window position, fed by
 * the map every camera frame so the card rides along.
 */
@Composable
internal fun StopPopover(
    index: Int,
    waypoint: Waypoint,
    count: Int,
    anchor: Offset,
    stayAtDestination: Boolean,
    onSetWait: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isEnd = index == count - 1 && count >= 2
    val hasWait = waypoint.waitSeconds > 0
    val waitLabel = formatDurationShort(waypoint.waitSeconds.toDouble())
    MapPopover(anchor = anchor, onDismiss = onDismiss, modal = false) {
        Row(
            modifier = Modifier.padding(horizontal = Tokens.space4, vertical = Tokens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StopDisc(number = index + 1, isStart = index == 0, isEnd = isEnd)
            Spacer(Modifier.width(Tokens.space3))
            Column {
                Text(
                    text = stopName(index, count),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                if (hasWait) {
                    Text(
                        text = stringResource(R.string.sheet_waits, waitLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MockarrTheme.colors.hold,
                    )
                }
            }
        }
        if (isEnd && stayAtDestination) {
            // The Stay option (Options sheet) already parks the drive here: say so, greyed.
            PopoverRow(
                label = stringResource(R.string.stop_menu_stays),
                icon = painterResource(R.drawable.ic_schedule),
                enabled = false,
                onClick = {},
            )
        } else {
            PopoverRow(
                label = if (hasWait) {
                    stringResource(R.string.stop_menu_wait_set, waitLabel)
                } else {
                    stringResource(R.string.stop_menu_wait)
                },
                icon = painterResource(R.drawable.ic_schedule),
                onClick = onSetWait,
            )
        }
        PopoverRow(
            label = stringResource(R.string.stop_menu_move),
            icon = painterResource(R.drawable.ic_target),
            onClick = onMove,
        )
        PopoverRow(
            label = stringResource(R.string.stop_menu_delete),
            icon = rememberVectorPainter(Icons.Filled.Delete),
            destructive = true,
            onClick = onDelete,
        )
    }
}

/** "Start" / "Stop 2" / "Destination" — the same words the sheet's stop list uses. */
@Composable
internal fun stopName(index: Int, count: Int): String = when {
    index == 0 -> stringResource(R.string.sheet_start)
    index == count - 1 && count >= 2 -> stringResource(R.string.sheet_destination)
    else -> stringResource(R.string.sheet_stop_n, index + 1)
}
