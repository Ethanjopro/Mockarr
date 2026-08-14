package dev.mockarr.app.ui.map

import dev.mockarr.core.model.LatLng

/** One-shot request to move the camera (e.g. after a place search). */
data class CameraCommand(
    val target: LatLng,
    val zoom: Double,
    val seq: Long,
)
