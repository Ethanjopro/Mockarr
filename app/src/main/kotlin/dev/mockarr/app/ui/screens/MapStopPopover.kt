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
import dev.mockarr.app.ui.rememberFormatter
import dev.mockarr.app.ui.theme.MapPopover
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.PopoverRow
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.Waypoint

/**
 * Strava's tap-a-point callout, over the marker: the stop's name and wait,
 * then Wait (or a greyed "Stays at destination" on the last stop while that option is on)
 * · Move · Delete. While [playing] only the Wait row shows — mid-drive the
 * route's shape is fixed, but a coming stop's wait can still change. [anchor]
 * is the marker's window position, fed by the map every camera frame so the
 * card rides along.
 */
@Composable
internal fun StopPopover(
    index: Int,
    waypoint: Waypoint,
    count: Int,
    anchor: Offset,
    stayAtDestination: Boolean,
    playing: Boolean,
    onSetWait: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isEnd = index == count - 1 && count >= 2
    val hasWait = waypoint.waitSeconds > 0
    val waitLabel = rememberFormatter().duration(waypoint.waitSeconds.toDouble())
    MapPopover(anchor = anchor, onDismiss = onDismiss, modal = false) {
        Row(
            modifier = Modifier.padding(horizontal = Tokens.space3, vertical = Tokens.space2),
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
        val stays = stopStays(index, count, stayAtDestination)
        PopoverRow(
            label = when {
                stays -> stringResource(R.string.stop_menu_stays)
                hasWait -> stringResource(R.string.stop_menu_wait_set, waitLabel)
                else -> stringResource(R.string.stop_menu_wait)
            },
            icon = painterResource(R.drawable.ic_schedule),
            enabled = !stays,
            onClick = onSetWait,
            // The header/action boundary reads through spacing; hairlines only separate actions.
            divider = false,
        )
        if (!playing) {
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
}

/** The last stop's wait is moot while "Stay at destination" parks the drive there (greyed row). */
internal fun stopStays(index: Int, count: Int, stayAtDestination: Boolean): Boolean =
    stayAtDestination && index == count - 1 && count >= 2

/** "Start" / "Stop 2" / "Destination" — the same words the sheet's stop list uses. */
@Composable
internal fun stopName(index: Int, count: Int): String = when {
    index == 0 -> stringResource(R.string.sheet_start)
    index == count - 1 && count >= 2 -> stringResource(R.string.sheet_destination)
    else -> stringResource(R.string.sheet_stop_n, index + 1)
}
