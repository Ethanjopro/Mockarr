package dev.mockarr.app.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import dev.mockarr.app.R
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.core.model.PlaybackState
import kotlinx.coroutines.delay

/**
 * Invokes [onArrived] once each time playback ends by reaching its destination
 * on its own — never after Finish or Stop. Both endings finish in the same
 * engine state, but a Stop's multi-second deceleration always shows the UI
 * `Stopping` frames first; a natural arrival never does.
 */
@Composable
internal fun ArrivalEffect(session: MockSessionState, playbackState: PlaybackState?, onArrived: () -> Unit) {
    val playing = session is MockSessionState.Playing
    var wasPlaying by remember { mutableStateOf(playing) }
    // The engine's last word before it ended: Stopping means the user cut the drive short.
    var lastPlayback by remember { mutableStateOf(playbackState) }
    SideEffect { if (playbackState != null) lastPlayback = playbackState }
    LaunchedEffect(playing) {
        val ended = wasPlaying && !playing
        wasPlaying = playing
        if (ended && lastPlayback !is PlaybackState.Stopping) onArrived()
    }
}

/**
 * The end of a drive, noticed: true for a few seconds after a natural arrival
 * (per [ArrivalEffect]), with one haptic tick. The strip wears it as
 * "Arrived at …" before settling into Holding — the last frame of a drive is
 * the one that gets remembered.
 */
@Composable
internal fun rememberArrived(session: MockSessionState, playbackState: PlaybackState?): Boolean {
    val playing = session is MockSessionState.Playing
    var arrivedTick by remember { mutableStateOf(0) }
    ArrivalEffect(session, playbackState) { arrivedTick++ }
    var arrived by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    // Keyed on playing too: a new drive starting mid-window drops the flag.
    LaunchedEffect(arrivedTick, playing) {
        if (arrivedTick > 0 && !playing) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
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
