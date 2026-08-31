package dev.mockarr.core.simulation

/**
 * Tuning knobs for route playback realism. Defaults follow PLAN.md §4.
 */
data class SimulationParams(
    val tickHz: Double = 1.0,
    val accelerationMps2: Double = 2.0,
    val decelerationMps2: Double = 3.0,
    /** Congestion multiplier ≥ 1 slows cruise speeds and stretches ETAs (see TrafficModel). */
    val durationScale: Double = 1.0,
    /** Per-segment random speed spread (fraction, e.g. 0.08 = ±8%) mimicking driver behavior. */
    val speedVarianceFraction: Double = 0.0,
    /** Brief stop where the route leaves the road for an off-road connector; 0 = off. */
    val offRoadPauseSeconds: Int = 0,
    val jitterEnabled: Boolean = true,
    val jitterSigmaMeters: Double = 3.0,
    val minAccuracyMeters: Double = 3.0,
    val maxAccuracyMeters: Double = 8.0,
)
