package dev.mockarr.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
 * ends, and one as each intermediate stop ([stops], as fractions of the route)
 * is passed without a wait — the same beats the notification's progress points
 * mark. The system haptic setting silences all of it.
 */
@Composable
internal fun SessionHaptics(session: MockSessionState, playbackState: State<PlaybackState?>, stops: List<Float>) {
    val haptic = LocalHapticFeedback.current
    val playing = session is MockSessionState.Playing
    var wasPlaying by remember { mutableStateOf(playing) }
    LaunchedEffect(playing) {
        if (playing && !wasPlaying) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        wasPlaying = playing
    }
    // Derived, so this scope wakes only when a wait begins or ends or a stop is passed —
    // never on the fixes in between.
    val dwelling by remember { derivedStateOf { playbackState.value is PlaybackState.Dwelling } }
    val waiting = playing && dwelling
    var wasWaiting by remember { mutableStateOf(waiting) }
    LaunchedEffect(waiting) {
        if (waiting != wasWaiting && playing) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        wasWaiting = waiting
    }
    // A stop with a wait ticks as the wait begins (above); a stop driven straight
    // through ticks here. The count is only trusted between two moving readings,
    // so a pause, a wait or any transient state re-bases it instead of ticking;
    // the tolerance covers the rounding between the bar's fractions and the
    // engine's progress, so a dwell never ticks twice.
    val movingPassed by remember(stops) {
        derivedStateOf {
            (playbackState.value as? PlaybackState.Playing)?.let { stopsPassed(stops, it.progress, PASSED_TOLERANCE) }
        }
    }
    val passed = if (playing) movingPassed else null
    var wasPassed by remember { mutableStateOf(passed) }
    LaunchedEffect(passed) {
        val previous = wasPassed
        if (passed != null && previous != null && passed > previous) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        wasPassed = passed
    }
}

private const val PASSED_TOLERANCE = 0.002
