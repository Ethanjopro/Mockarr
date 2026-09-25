package dev.mockarr.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import dev.mockarr.app.R
import dev.mockarr.app.playback.MockSessionRepository

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
    drive: MockSessionRepository.LiveDrive?,
    /** How many of the drive's stops are behind it ([stopsPassed]). */
    passed: Int,
    stayAtDestination: Boolean,
    onSetWait: (Int) -> Unit,
): List<CustomAccessibilityAction> {
    // Progress runs along what the engine drives, so the stops' positions come from it too;
    // a drive-in's origin shifts its waypoint count by one.
    val driven = drive?.route ?: state.route
    val offset = drive?.stopOffset ?: 0
    val stops = remember(driven) { stopFractions(driven).size }
    val last = state.waypoints.lastIndex
    val stopLabel = stringResource(R.string.stat_set_wait_stop_cd)
    val namedLabel = stringResource(R.string.stat_set_wait_named_cd)
    val destinationLabel = stringResource(R.string.stat_set_wait_destination_cd)
    return (1..last).mapNotNull { index ->
        val destination = index == last
        // The route's stop sits at fraction index + offset - 1; it is ahead until passed.
        val fraction = index + offset - 1
        val ahead = destination || (fraction in passed until stops)
        val name = state.waypoints[index].name
        val label = when {
            name != null -> namedLabel.format(name)
            destination -> destinationLabel
            else -> stopLabel.format(index + 1)
        }
        if (!ahead || (destination && stayAtDestination)) {
            null
        } else {
            CustomAccessibilityAction(label) {
                onSetWait(index)
                true
            }
        }
    }
}
