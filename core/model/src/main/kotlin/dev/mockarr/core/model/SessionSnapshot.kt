package dev.mockarr.core.model

import kotlinx.serialization.Serializable

/**
 * What the mock session was doing the last time it was written down, so a
 * drive or hold cut short by process death can be offered back on the next
 * launch. Written by the playback service, cleared when the session ends on
 * its own terms.
 */
@Serializable
data class SessionSnapshot(
    val kind: Kind,
    val route: Route? = null,
    val profile: RoutingProfile = RoutingProfile.DRIVING,
    val speedMultiplier: Double = 1.0,
    /** Along-track metres driven when the snapshot was taken (playing only). */
    val distanceMeters: Double = 0.0,
    /** Seconds still to wait at the stop the drive was dwelling at, else 0. */
    val dwellSecondsLeft: Double = 0.0,
    val holdPosition: LatLng? = null,
    val savedAtEpochMillis: Long,
) {
    @Serializable
    enum class Kind { PLAYING, HOLDING }

    /** Fraction of the route driven, 0 when nothing is known. */
    val progress: Double
        get() {
            val total = route?.distanceMeters ?: return 0.0
            return if (total <= 0.0) 0.0 else (distanceMeters / total).coerceIn(0.0, 1.0)
        }
}
