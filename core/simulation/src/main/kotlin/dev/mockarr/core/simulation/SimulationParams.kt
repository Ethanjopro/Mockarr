package dev.mockarr.core.simulation

/**
 * Tuning knobs for route playback realism. Defaults follow PLAN.md §4.
 */
data class SimulationParams(
    val tickHz: Double = 1.0,
    val accelerationMps2: Double = 2.0,
    val decelerationMps2: Double = 3.0,
    val jitterEnabled: Boolean = true,
    val jitterSigmaMeters: Double = 3.0,
    val minAccuracyMeters: Double = 3.0,
    val maxAccuracyMeters: Double = 8.0,
)
