package dev.mockarr.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import dev.mockarr.app.R
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.progressOrZero

/*
 * TalkBack paths for what the map only offers to a finger. The map is one node to
 * a screen reader, so every marker gesture needs a twin reachable from the card.
 */
/**
 * TalkBack's way to a coming stop's wait during a drive — the marker popover is
 * the only visual path, and the map is one node to a screen reader. One action
 * per stop still ahead; the destination only when it doesn't already stay put.
 */
@Composable
internal fun upcomingWaitActions(
    state: MapViewModel.UiState,
    playbackState: PlaybackState?,
    stayAtDestination: Boolean,
    onSetWait: (Int) -> Unit,
): List<CustomAccessibilityAction> {
    val progress = playbackState.progressOrZero
    val fractions = remember(state.route) { stopFractions(state.route) }
    val last = state.waypoints.lastIndex
    val stopLabel = stringResource(R.string.stat_set_wait_stop_cd)
    val destinationLabel = stringResource(R.string.stat_set_wait_destination_cd)
    return (1..last).mapNotNull { index ->
        val destination = index == last
        val ahead = if (destination) true else fractions.getOrNull(index - 1)?.let { it > progress } ?: false
        when {
            !ahead -> null
            destination && stayAtDestination -> null
            destination -> CustomAccessibilityAction(destinationLabel) {
                onSetWait(index)
                true
            }
            else -> CustomAccessibilityAction(stopLabel.format(index + 1)) {
                onSetWait(index)
                true
            }
        }
    }
}
