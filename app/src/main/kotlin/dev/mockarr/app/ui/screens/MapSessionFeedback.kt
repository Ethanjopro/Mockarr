package dev.mockarr.app.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import dev.mockarr.app.R
import dev.mockarr.app.playback.DriveOutcome
import dev.mockarr.app.playback.MockSessionState
import kotlinx.coroutines.delay

/**
 * Invokes [onArrived] once each time playback ends by reaching its destination
 * on its own — never after Finish or Stop. [outcome] is read when the drive
 * ends: the repository records it before the session leaves Playing, so this
 * holds even when the app was away for the whole deceleration (Stop pressed in
 * the notification).
 */
@Composable
internal fun ArrivalEffect(session: MockSessionState, outcome: () -> DriveOutcome?, onArrived: () -> Unit) {
    val playing = session is MockSessionState.Playing
    var wasPlaying by remember { mutableStateOf(playing) }
    LaunchedEffect(playing) {
        val ended = wasPlaying && !playing
        wasPlaying = playing
        if (ended && outcome() == DriveOutcome.ARRIVED) onArrived()
    }
}

/**
 * The end of a drive, noticed: true for a few seconds after a natural arrival
 * (per [ArrivalEffect]), with one haptic tick. The strip wears it as
 * "Arrived at …" before settling into Holding — the last frame of a drive is
 * the one that gets remembered.
 */
@Composable
internal fun rememberArrived(session: MockSessionState, outcome: () -> DriveOutcome?): Boolean {
    val playing = session is MockSessionState.Playing
    var arrivedTick by remember { mutableStateOf(0) }
    ArrivalEffect(session, outcome) { arrivedTick++ }
    var arrived by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    // Keyed on playing too: a new drive starting mid-window drops the flag.
    LaunchedEffect(arrivedTick, playing) {
        if (arrivedTick > 0 && !playing) {
            // The end-of-drive chain (engine ended → hold) relaunches this once
            // more within the window: the band stays, the tick is not repeated.
            if (!arrived) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            arrived = true
            delay(ARRIVED_MILLIS)
            arrived = false
        } else {
            arrived = false
        }
    }
    return arrived
}

/**
 * The moment the real location becomes visible to other apps again — the
 * zero-leak transition — is confirmed out loud. Any active session ending in
 * Idle (Stop on the strip, Finish with "Stay at destination" off) shows it.
 */
@Composable
internal fun ReleaseSnackbar(session: MockSessionState, snackbarHostState: SnackbarHostState) {
    val message = stringResource(R.string.snack_released)
    var previous by remember { mutableStateOf(session) }
    LaunchedEffect(session) {
        val released = previous !is MockSessionState.Idle && session is MockSessionState.Idle
        previous = session
        if (released) snackbarHostState.showSnackbar(message)
    }
}

private const val ARRIVED_MILLIS = 4_000L
