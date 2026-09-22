package dev.mockarr.app.playback

import dev.mockarr.app.ui.DurationParts
import dev.mockarr.app.ui.durationParts
import dev.mockarr.app.ui.fromMeters
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.PlaybackState
import kotlin.math.roundToLong

/**
 * The drive notification reduced to what a person can see change: the bar in
 * tenths of a percent, the phase, the time left as the copy words it, and the
 * distance driven to the tenth of a km / mi the copy prints. Equal keys mean
 * there is nothing new to post.
 */
internal data class DriveNotificationKey(
    val permille: Int,
    val phase: Phase,
    val timeLeft: DurationParts,
    val drivenTenths: Long,
) {
    enum class Phase { MOVING, PAUSED, WAITING }
}

/**
 * Null while the drive is stopping or finished: those report no progress, and
 * posting them would flash the bar back to empty during the deceleration.
 */
internal fun driveNotificationKey(
    state: PlaybackState?,
    routeMeters: Double,
    units: DistanceUnits,
): DriveNotificationKey? {
    val (progress, remainingSeconds, phase) = when (state) {
        is PlaybackState.Playing -> Triple(state.progress, state.remainingSeconds, DriveNotificationKey.Phase.MOVING)
        is PlaybackState.Paused -> Triple(state.progress, state.remainingSeconds, DriveNotificationKey.Phase.PAUSED)
        is PlaybackState.Dwelling -> Triple(state.progress, state.remainingSeconds, DriveNotificationKey.Phase.WAITING)
        else -> return null
    }
    return DriveNotificationKey(
        permille = (progress.coerceIn(0.0, 1.0) * PERMILLE).toInt(),
        phase = phase,
        timeLeft = durationParts(remainingSeconds),
        drivenTenths = (units.fromMeters(routeMeters * progress) * TENTHS).roundToLong(),
    )
}

private const val PERMILLE = 1_000
private const val TENTHS = 10
