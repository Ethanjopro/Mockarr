package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.SimulatedFix
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Drives one route playback session. Collecting [fixes] runs the tick loop —
 * the flow is cold and single-collector; cancelling the collection aborts the
 * session. Pure Kotlin: time comes from [clock], randomness from [random], so
 * tests are deterministic under virtual time.
 */
class SimulationEngine(
    route: Route,
    private val params: SimulationParams = SimulationParams(),
    private val clock: SimClock = SimClock { System.nanoTime() },
    private val random: Random = Random(0),
    initialSpeedMultiplier: Double = 1.0,
) {
    private val geometry = RouteGeometry(route, params.decelerationMps2)

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Playing(0.0))
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    @Volatile
    private var speedMultiplier = initialSpeedMultiplier.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)

    private var distance = 0.0
    private var speed = 0.0
    private var smoothedBearing = geometry.bearingAt(0.0)

    val fixes: Flow<SimulatedFix> = flow {
        var lastNanos = clock.elapsedNanos()
        emit(currentFix()) // land on the route start immediately
        var running = true
        while (running && currentCoroutineContext().isActive) {
            delay(tickMillis)
            val now = clock.elapsedNanos()
            val dt = ((now - lastNanos) / NANOS_PER_SECOND).coerceIn(MIN_DT, MAX_DT)
            lastNanos = now
            tick(dt)?.let { emit(it) }
            running = _state.value !is PlaybackState.Finished
        }
    }

    /** Advances one tick; returns the fix to emit, or null when the session is over. */
    private fun tick(dt: Double): SimulatedFix? = when (_state.value) {
        is PlaybackState.Playing -> {
            step(dt)
            val fix = currentFix()
            if (distance >= geometry.totalDistanceMeters - END_EPSILON_METERS) {
                finish()
            }
            fix
        }
        is PlaybackState.Paused -> currentFix()
        PlaybackState.Stopping -> {
            stopStep(dt)
            if (speed <= STOP_SPEED_THRESHOLD) {
                speed = 0.0
                _state.value = PlaybackState.Finished
            }
            currentFix()
        }
        PlaybackState.Finished -> null
    }

    fun pause() {
        val current = _state.value
        if (current is PlaybackState.Playing) {
            speed = 0.0
            _state.value = PlaybackState.Paused(current.progress)
        }
    }

    fun resume() {
        val current = _state.value
        if (current is PlaybackState.Paused) {
            _state.value = PlaybackState.Playing(current.progress)
        }
    }

    fun stop() {
        if (_state.value is PlaybackState.Playing || _state.value is PlaybackState.Paused) {
            _state.value = PlaybackState.Stopping
        }
    }

    fun setSpeedMultiplier(multiplier: Double) {
        speedMultiplier = multiplier.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
    }

    private val tickMillis: Long = (MILLIS_PER_SECOND / params.tickHz).toLong().coerceAtLeast(1L)

    private fun step(dt: Double) {
        val segment = geometry.segmentIndexAt(distance)
        val nextVertex = segment + 1
        val cruise = geometry.segmentSpeeds[segment] * speedMultiplier
        val allowedAtVertex = geometry.allowedVertexSpeeds[nextVertex] * speedMultiplier
        val distToVertex = geometry.distanceToVertex(distance, nextVertex)
        val target = min(
            cruise,
            RouteGeometry.brakingLimit(allowedAtVertex, params.decelerationMps2, distToVertex),
        )
        speed = if (target >= speed) {
            min(speed + params.accelerationMps2 * dt, target)
        } else {
            max(speed - params.decelerationMps2 * dt, target)
        }
        advance(dt)
        _state.value = PlaybackState.Playing(progress())
    }

    private fun stopStep(dt: Double) {
        speed = max(0.0, speed - params.decelerationMps2 * dt)
        advance(dt)
    }

    private fun advance(dt: Double) {
        distance = (distance + speed * dt).coerceAtMost(geometry.totalDistanceMeters)
        val target = geometry.bearingAt(distance)
        smoothedBearing = normalizeBearing(
            smoothedBearing + RouteGeometry.shortestAngleDelta(smoothedBearing, target) * BEARING_SMOOTHING,
        )
    }

    private fun finish() {
        distance = geometry.totalDistanceMeters
        speed = 0.0
        _state.value = PlaybackState.Finished
    }

    private fun progress(): Double =
        if (geometry.totalDistanceMeters <= 0.0) 1.0 else distance / geometry.totalDistanceMeters

    private fun currentFix(): SimulatedFix {
        val truePosition = geometry.positionAt(distance)
        val reported = if (params.jitterEnabled) jitter(truePosition) else truePosition
        val accuracy = (params.minAccuracyMeters + abs(gaussian()) * ACCURACY_SIGMA)
            .coerceIn(params.minAccuracyMeters, params.maxAccuracyMeters)
        return SimulatedFix(
            position = reported,
            speedMetersPerSecond = speed,
            bearingDegrees = smoothedBearing,
            accuracyMeters = accuracy,
            altitudeMeters = DEFAULT_ALTITUDE_METERS,
        )
    }

    /** Jitter the reported position only — along-track state never drifts. */
    private fun jitter(position: LatLng): LatLng {
        val offsetMeters = abs(gaussian()) * params.jitterSigmaMeters
        val direction = random.nextDouble() * FULL_CIRCLE_DEGREES
        return GeoMath.destination(position, direction, offsetMeters)
    }

    private var spareGaussian: Double? = null

    /** Box–Muller, seeded via [random] for deterministic tests. */
    private fun gaussian(): Double {
        spareGaussian?.let {
            spareGaussian = null
            return it
        }
        var u1 = random.nextDouble()
        while (u1 <= 1e-12) u1 = random.nextDouble()
        val u2 = random.nextDouble()
        val radius = sqrt(-2.0 * ln(u1))
        spareGaussian = radius * sin(2.0 * PI * u2)
        return radius * cos(2.0 * PI * u2)
    }

    private fun normalizeBearing(bearing: Double): Double = ((bearing % 360.0) + 360.0) % 360.0

    companion object {
        const val MIN_MULTIPLIER = 0.25
        const val MAX_MULTIPLIER = 4.0
        private const val NANOS_PER_SECOND = 1e9
        private const val MILLIS_PER_SECOND = 1_000.0
        private const val MIN_DT = 0.001
        private const val MAX_DT = 5.0
        private const val END_EPSILON_METERS = 0.05
        private const val STOP_SPEED_THRESHOLD = 0.3
        private const val BEARING_SMOOTHING = 0.4
        private const val ACCURACY_SIGMA = 1.5
        private const val FULL_CIRCLE_DEGREES = 360.0
        private const val DEFAULT_ALTITUDE_METERS = 35.0
    }
}
