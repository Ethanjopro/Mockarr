package dev.mockarr.core.model

import kotlinx.serialization.Serializable

/** A route-builder stop: where it is, and how long playback lingers there. */
@Serializable
data class Waypoint(
    val position: LatLng,
    /** Seconds playback dwells at this stop before continuing; 0 = no wait. */
    val waitSeconds: Int = 0,
)
