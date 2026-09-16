package dev.mockarr.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.mockarr.app.playback.MockSessionState
import dev.mockarr.core.model.PlaybackState

/**
 * The drive's touch vocabulary (arrival already ticks in [rememberArrived]):
 * a firm tick when a drive starts, a light one when a wait begins and when it
 * ends. The system haptic setting silences all of it.
 */
@Composable
internal fun SessionHaptics(session: MockSessionState, playbackState: PlaybackState?) {
    val haptic = LocalHapticFeedback.current
    val playing = session is MockSessionState.Playing
    var wasPlaying by remember { mutableStateOf(playing) }
    LaunchedEffect(playing) {
        if (playing && !wasPlaying) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        wasPlaying = playing
    }
    val waiting = playing && playbackState is PlaybackState.Dwelling
    var wasWaiting by remember { mutableStateOf(waiting) }
    LaunchedEffect(waiting) {
        if (waiting != wasWaiting && playing) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        wasWaiting = waiting
    }
}
