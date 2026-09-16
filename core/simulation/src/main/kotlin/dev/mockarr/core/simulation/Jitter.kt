package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GPS-style position noise shared by the playback engine and the stationary
 * hold: a half-normal radius (σ = [offset]'s `sigmaMeters`) in a uniform
 * direction, applied to the *reported* position only so nothing accumulates.
 * Seeded through [random] for deterministic tests.
 */
class Jitter(private val random: kotlin.random.Random) {

    private var spareGaussian: Double? = null

    /** Standard normal sample (Box–Muller, one spare kept). */
    fun gaussian(): Double {
        spareGaussian?.let {
            spareGaussian = null
            return it
        }
        var u1 = random.nextDouble()
        while (u1 <= MIN_UNIFORM) u1 = random.nextDouble()
        val u2 = random.nextDouble()
        val radius = sqrt(-2.0 * ln(u1))
        spareGaussian = radius * sin(2.0 * PI * u2)
        return radius * cos(2.0 * PI * u2)
    }

    /** [position] displaced by |N(0, σ)| metres in a random direction; unchanged when σ ≤ 0. */
    fun offset(position: LatLng, sigmaMeters: Double): LatLng {
        val offsetMeters = abs(gaussian()) * sigmaMeters
        val direction = random.nextDouble() * FULL_CIRCLE_DEGREES
        if (sigmaMeters <= 0.0) return position
        return GeoMath.destination(position, direction, offsetMeters)
    }

    private companion object {
        const val MIN_UNIFORM = 1e-12
        const val FULL_CIRCLE_DEGREES = 360.0
    }
}
