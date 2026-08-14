package dev.mockarr.core.model

/** A saved map viewport — where the camera was last left. */
data class MapCamera(
    val target: LatLng,
    val zoom: Double,
)
