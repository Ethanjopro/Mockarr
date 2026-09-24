package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import dev.mockarr.app.R
import dev.mockarr.app.ui.rememberFormatter
import dev.mockarr.app.ui.theme.MapPopover
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.Waypoint

/**
 * Strava's tap-a-point callout over the marker, as symbols only: Move
 * (four-way arrows) · Wait (clock — hold-tinted once a wait is set, greyed
 * on the last stop while "Stay at destination" is on) · Delete (trash, error
 * ink). No header: the selected disc says which stop, and the words live in
 * the buttons' descriptions for TalkBack ("Wait · 5 min"). While [playing]
 * only the clock shows — mid-drive the route's shape is fixed, but a coming
 * stop's wait can still change. [anchor] is the marker's window position,
 * fed by the map every camera frame so the card rides along.
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
    /** The destination holds anyway ("Stay at destination"): say why there's no wait to set. */
    onExplainStays: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasWait = waypoint.waitSeconds > 0
    val waitLabel = rememberFormatter().duration(waypoint.waitSeconds.toDouble())
    val stays = stopStays(index, count, stayAtDestination)
    val groupLabel = stringResource(R.string.stop_popover_cd, stopName(index, count))
    val waitDescription = when {
        stays -> stringResource(R.string.stop_menu_stays)
        hasWait -> stringResource(R.string.stop_menu_wait_set, waitLabel)
        else -> stringResource(R.string.stop_menu_wait)
    }
    val scheme = MaterialTheme.colorScheme
    val waitInk = when {
        // Still greyed — there's no wait to set — but a tap explains why instead of doing nothing.
        stays -> scheme.onSurfaceVariant
        hasWait -> MockarrTheme.colors.hold
        else -> scheme.onSurface
    }
    MapPopover(anchor = anchor, onDismiss = onDismiss, modal = false) {
        Row(
            modifier = Modifier
                .padding(Tokens.space1)
                .semantics {
                    contentDescription = groupLabel
                    paneTitle = groupLabel
                    isTraversalGroup = true
                },
            horizontalArrangement = Arrangement.spacedBy(Tokens.space1),
        ) {
            if (!playing) {
                IconButton(onClick = onMove, modifier = Modifier.size(Tokens.touchTarget)) {
                    Icon(
                        painterResource(R.drawable.ic_open_with),
                        contentDescription = stringResource(R.string.stop_menu_move),
                    )
                }
            }
            IconButton(
                onClick = if (stays) onExplainStays else onSetWait,
                colors = IconButtonDefaults.iconButtonColors(contentColor = waitInk),
                modifier = Modifier.size(Tokens.touchTarget),
            ) {
                Icon(painterResource(R.drawable.ic_schedule), contentDescription = waitDescription)
            }
            if (!playing) {
                // Neutral like its neighbours: the glyph says "remove", no red needed.
                IconButton(onClick = onDelete, modifier = Modifier.size(Tokens.touchTarget)) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.stop_menu_delete),
                    )
                }
            }
        }
    }
}

/** The last stop's wait is moot while "Stay at destination" parks the drive there (greyed button). */
internal fun stopStays(index: Int, count: Int, stayAtDestination: Boolean): Boolean =
    stayAtDestination && index == count - 1 && count >= 2

/** "Start" / "Stop 2" / "Destination" — the same words the sheet's stop list uses. */
@Composable
internal fun stopName(index: Int, count: Int): String = when {
    index == 0 -> stringResource(R.string.sheet_start)
    index == count - 1 && count >= 2 -> stringResource(R.string.sheet_destination)
    else -> stringResource(R.string.sheet_stop_n, index + 1)
}
