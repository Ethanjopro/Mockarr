package dev.mockarr.core.model

import kotlinx.serialization.Serializable

/** A geographic coordinate in decimal degrees (WGS 84). */
@Serializable
data class LatLng(
    val latitude: Double,
    val longitude: Double,
)
