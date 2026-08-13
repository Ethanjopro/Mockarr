package dev.mockarr.core.model

/** One simulated location fix emitted by the playback engine. */
data class SimulatedFix(
    val position: LatLng,
    val speedMetersPerSecond: Double,
    val bearingDegrees: Double,
    val accuracyMeters: Double,
    val altitudeMeters: Double,
)
