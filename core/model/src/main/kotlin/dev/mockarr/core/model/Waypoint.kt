package dev.mockarr.core.model

import kotlinx.serialization.Serializable

/** A route-builder stop: where it is, what it's called, and how long playback lingers there. */
@Serializable
data class Waypoint(
    val position: LatLng,
    /** Seconds playback dwells at this stop before continuing; 0 = no wait. */
    val waitSeconds: Int = 0,
    /**
     * What a person calls the spot: the search result it came from, or its reverse-geocoded
     * street; null until the lookup lands (the UI falls back to "Stop 2").
     */
    val name: String? = null,
)
