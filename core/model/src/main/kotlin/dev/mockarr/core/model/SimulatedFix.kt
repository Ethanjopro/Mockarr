package dev.mockarr.core.model

/** One simulated location fix emitted by the playback engine. */
data class SimulatedFix(
    /** What other apps are told: the true position plus the GPS wobble, when that is on. */
    val position: LatLng,
    /** Where the simulation really is on the route — the centre the wobble scatters around. */
    val truePosition: LatLng,
    val speedMetersPerSecond: Double,
    val bearingDegrees: Double,
    val accuracyMeters: Double,
    val altitudeMeters: Double,
)
