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
    private val geometry = RouteGeometry(
        route,
        params.decelerationMps2,
        params.durationScale,
        params.speedVarianceFraction,
        random,
        params.offRoadPauseSeconds,
    )

    @Volatile
    private var speedMultiplier = initialSpeedMultiplier.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)

    /** Copy-on-write: replaced wholesale by [applyWaitEdit] on the tick coroutine. */
    private var dwellStops = geometry.dwellStops
    private var nextDwellIndex = 0

    /** Wait edits queued by any thread, drained at the top of each tick. */
    @Volatile
    private var pendingWaitEdits: List<Pair<Int, Int>> = emptyList()

    /** Countdown of the active dwell; survives pause/resume, unlike the state object. */
    private var dwellSecondsLeft = 0.0

    private val _state = MutableStateFlow<PlaybackState>(
        PlaybackState.Playing(
            0.0,
            geometry.totalDurationSeconds / speedMultiplier + dwellStops.sumOf { it.waitSeconds },
        ),
    )
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

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
    private fun tick(dt: Double): SimulatedFix? {
        drainWaitEdits()
        return tickState(dt)
    }

    private fun tickState(dt: Double): SimulatedFix? = when (_state.value) {
        is PlaybackState.Playing -> {
            step(dt)
            val fix = currentFix()
            // A destination wait dwells first; the finish comes once it has run out.
            val reachedEnd = distance >= geometry.totalDistanceMeters - END_EPSILON_METERS
            if (_state.value is PlaybackState.Playing && reachedEnd) {
                finish()
            }
            fix
        }
        is PlaybackState.Dwelling -> dwellTick(dt)
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
        when (val current = _state.value) {
            is PlaybackState.Playing -> {
                speed = 0.0
                _state.value = PlaybackState.Paused(current.progress, current.remainingSeconds)
            }
            is PlaybackState.Dwelling ->
                _state.value = PlaybackState.Paused(current.progress, current.remainingSeconds)
            else -> Unit
        }
    }

    fun resume() {
        val current = _state.value
        if (current is PlaybackState.Paused) {
            _state.value = if (dwellSecondsLeft > 0.0) {
                dwelling(current.progress, current.remainingSeconds, dwellStops.getOrNull(nextDwellIndex))
            } else {
                PlaybackState.Playing(current.progress, current.remainingSeconds)
            }
        }
    }

    /**
     * True once [stop] cut the run short of the destination. Stopping during the
     * destination wait still counts as arrived.
     */
    var stoppedBeforeArrival: Boolean = false
        private set

    fun stop() {
        val current = _state.value
        val stoppable = current is PlaybackState.Playing ||
            current is PlaybackState.Paused ||
            current is PlaybackState.Dwelling
        if (stoppable) {
            stoppedBeforeArrival = !(current is PlaybackState.Dwelling && current.isDestination)
            _state.value = PlaybackState.Stopping
        }
    }

    fun setSpeedMultiplier(multiplier: Double) {
        speedMultiplier = multiplier.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
    }

    /**
     * Updates one stop's wait mid-run (applied on the next tick). A stop already
     * passed is a no-op; the active dwell's countdown is adjusted in place; a
     * wait added to a previously unwaited stop dwells there when the drive
     * arrives — with an abrupt brake, since its vertex kept its cruise speed.
     */
    fun setWaypointWait(waypointIndex: Int, waitSeconds: Int) {
        pendingWaitEdits = pendingWaitEdits + (waypointIndex to waitSeconds)
    }

    private fun drainWaitEdits() {
        val edits = pendingWaitEdits
        if (edits.isEmpty()) return
        pendingWaitEdits = emptyList()
        for ((waypointIndex, waitSeconds) in edits) {
            applyWaitEdit(waypointIndex, waitSeconds)
        }
    }

    private fun applyWaitEdit(waypointIndex: Int, waitSeconds: Int) {
        val stops = dwellStops
        val existing = stops.indexOfFirst { it.waypointIndex == waypointIndex }
        // Active dwell (or one frozen by pause): the countdown itself is adjusted.
        val dwellingHere = existing >= 0 && existing == nextDwellIndex && dwellSecondsLeft > 0.0
        when {
            dwellingHere -> {
                val elapsed = stops[existing].waitSeconds - dwellSecondsLeft
                dwellSecondsLeft = (waitSeconds - elapsed).coerceAtLeast(0.0)
                dwellStops = stops.replaceWait(existing, waitSeconds)
            }
            // Behind the drive: the moment has passed, nothing to change.
            existing in 0 until nextDwellIndex -> Unit
            existing >= 0 && waitSeconds <= 0 -> dwellStops = stops.filterIndexed { i, _ -> i != existing }
            existing >= 0 -> dwellStops = stops.replaceWait(existing, waitSeconds)
            waitSeconds > 0 -> insertDwell(waypointIndex, waitSeconds)
            else -> Unit
        }
    }

    /** Adds a dwell for a stop that had none; lands sorted, always at/after [nextDwellIndex]. */
    private fun insertDwell(waypointIndex: Int, waitSeconds: Int) {
        val stopDistance = geometry.waypointDistanceMeters(waypointIndex) ?: return
        if (stopDistance <= distance) return // already driven past it
        val stop = RouteGeometry.DwellStop(
            stopDistance,
            waitSeconds,
            waypointIndex,
            isDestination = waypointIndex == geometry.lastWaypointIndex,
        )
        dwellStops = (dwellStops + stop).sortedBy { it.distanceMeters }
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
        val stop = dwellStops.getOrNull(nextDwellIndex)
        if (stop != null && distance >= stop.distanceMeters - DWELL_EPSILON_METERS) {
            distance = stop.distanceMeters // land the dwell fixes exactly on the stop
            speed = 0.0
            dwellSecondsLeft = stop.waitSeconds.toDouble()
            _state.value = dwelling(progress(), remainingWithDwell(), stop)
            return
        }
        _state.value = PlaybackState.Playing(progress(), remainingWithDwell())
    }

    /** Counts down the active dwell in real time — a wait is a wait at any speed multiplier. */
    private fun dwellTick(dt: Double): SimulatedFix {
        dwellSecondsLeft -= dt
        if (dwellSecondsLeft <= 0.0) {
            dwellSecondsLeft = 0.0
            nextDwellIndex++
            _state.value = PlaybackState.Playing(progress(), remainingWithDwell())
        } else {
            _state.value = dwelling(progress(), remainingWithDwell(), dwellStops[nextDwellIndex])
        }
        return currentFix()
    }

    private fun dwelling(progress: Double, remaining: Double, stop: RouteGeometry.DwellStop?) =
        PlaybackState.Dwelling(
            progress,
            remaining,
            dwellSecondsLeft,
            stop?.waypointIndex ?: -1,
            stop?.isDestination ?: false,
        )

    /** Multiplier-scaled cruise time to the destination plus every not-yet-elapsed dwell second (real time). */
    private fun remainingWithDwell(): Double {
        val futureFrom = if (dwellSecondsLeft > 0.0) nextDwellIndex + 1 else nextDwellIndex
        var dwell = dwellSecondsLeft
        for (i in futureFrom until dwellStops.size) {
            dwell += dwellStops[i].waitSeconds
        }
        return geometry.remainingDurationSeconds(distance) / speedMultiplier + dwell
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
            altitudeMeters = geometry.altitudeAt(distance) ?: DEFAULT_ALTITUDE_METERS,
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
        private const val DWELL_EPSILON_METERS = 0.5
        private const val STOP_SPEED_THRESHOLD = 0.3
        private const val BEARING_SMOOTHING = 0.4
        private const val ACCURACY_SIGMA = 1.5
        private const val FULL_CIRCLE_DEGREES = 360.0
        private const val DEFAULT_ALTITUDE_METERS = 35.0
    }
}

private fun List<RouteGeometry.DwellStop>.replaceWait(index: Int, waitSeconds: Int) =
    mapIndexed { i, stop -> if (i == index) stop.copy(waitSeconds = waitSeconds) else stop }
