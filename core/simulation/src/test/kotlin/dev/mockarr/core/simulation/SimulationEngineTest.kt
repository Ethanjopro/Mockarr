package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.SimulatedFix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationEngineTest {

    private val noJitter = SimulationParams(jitterEnabled = false)

    private fun TestScope.testClock() = SimClock { testScheduler.currentTime * 1_000_000 }

    /** Straight route east along the equator with uniform segment speed. */
    private fun straightRoute(
        lengthMeters: Double,
        segmentMeters: Double = 100.0,
        speedMps: Double = 10.0,
    ): Route {
        val start = LatLng(0.0, 0.0)
        val segments = (lengthMeters / segmentMeters).toInt()
        val points = mutableListOf(start)
        for (i in 1..segments) {
            points += GeoMath.destination(start, 90.0, segmentMeters * i)
        }
        val distances = List(segments) { segmentMeters }
        return Route(
            points = points,
            legs = listOf(
                RouteLeg(
                    segmentDistancesMeters = distances,
                    segmentDurationsSeconds = distances.map { it / speedMps },
                ),
            ),
            distanceMeters = segmentMeters * segments,
            durationSeconds = segmentMeters * segments / speedMps,
        )
    }

    /** 500 m east then 500 m north with a 90° corner, 15 m/s cruise. */
    private fun rightAngleRoute(speedMps: Double = 15.0): Route {
        val start = LatLng(0.0, 0.0)
        val points = mutableListOf(start)
        for (i in 1..10) points += GeoMath.destination(start, 90.0, 50.0 * i)
        val corner = points.last()
        for (i in 1..10) points += GeoMath.destination(corner, 0.0, 50.0 * i)
        val distances = List(20) { 50.0 }
        return Route(
            points = points,
            legs = listOf(
                RouteLeg(distances, distances.map { it / speedMps }),
            ),
            distanceMeters = 1000.0,
            durationSeconds = 1000.0 / speedMps,
        )
    }

    private suspend fun collectAll(engine: SimulationEngine): List<SimulatedFix> {
        val fixes = mutableListOf<SimulatedFix>()
        engine.fixes.collect { fixes += it }
        return fixes
    }

    @Test
    fun `completes close to the route duration`() = runTest {
        val route = straightRoute(1000.0) // 100 s at 10 m/s
        val engine = SimulationEngine(route, noJitter, testClock())
        val startMs = testScheduler.currentTime
        collectAll(engine)
        val elapsedSeconds = (testScheduler.currentTime - startMs) / 1000.0
        assertIs<PlaybackState.Finished>(engine.state.value)
        assertTrue(
            elapsedSeconds in 95.0..125.0,
            "expected ≈100–120 s (accel + final braking overhead), got $elapsedSeconds",
        )
    }

    @Test
    fun `speed never exceeds the segment target`() = runTest {
        val route = straightRoute(1000.0, speedMps = 10.0)
        val fixes = collectAll(SimulationEngine(route, noJitter, testClock()))
        val maxSpeed = fixes.maxOf { it.speedMetersPerSecond }
        assertTrue(maxSpeed <= 10.0 + 0.01, "max speed $maxSpeed")
    }

    @Test
    fun `acceleration stays within physical limits`() = runTest {
        val route = straightRoute(1000.0)
        val fixes = collectAll(SimulationEngine(route, noJitter, testClock()))
        fixes.zipWithNext().forEach { (a, b) ->
            val dv = b.speedMetersPerSecond - a.speedMetersPerSecond
            assertTrue(dv <= 2.0 + 0.01, "accelerated by $dv in one tick")
            assertTrue(dv >= -3.0 - 0.01, "decelerated by $dv in one tick")
        }
    }

    @Test
    fun `arrives exactly at the destination`() = runTest {
        val route = straightRoute(800.0)
        val fixes = collectAll(SimulationEngine(route, noJitter, testClock()))
        val end = route.points.last()
        val arrival = GeoMath.distanceMeters(fixes.last().position, end)
        assertTrue(arrival < 1.0, "finished $arrival m from the destination")
    }

    @Test
    fun `pause holds position and resume continues`() = runTest {
        val route = straightRoute(2000.0)
        val engine = SimulationEngine(route, noJitter, testClock())
        val fixes = mutableListOf<SimulatedFix>()
        val job = launch { engine.fixes.collect { fixes += it } }

        advanceTimeBy(20_000)
        engine.pause()
        advanceTimeBy(1_000)
        val pausedAt = fixes.last().position
        advanceTimeBy(10_000)
        val stillAt = fixes.last().position
        assertEquals(pausedAt, stillAt, "moved while paused")
        assertTrue(fixes.last().speedMetersPerSecond == 0.0)

        engine.resume()
        advanceTimeBy(10_000)
        assertTrue(
            GeoMath.distanceMeters(pausedAt, fixes.last().position) > 10.0,
            "did not move after resume",
        )
        engine.stop()
        advanceTimeBy(60_000)
        job.join()
    }

    @Test
    fun `stop decelerates without teleporting`() = runTest {
        val route = straightRoute(5000.0)
        val engine = SimulationEngine(route, noJitter, testClock())
        val fixes = mutableListOf<SimulatedFix>()
        val job = launch { engine.fixes.collect { fixes += it } }

        advanceTimeBy(30_000)
        engine.stop()
        advanceTimeBy(30_000)
        job.join()

        assertIs<PlaybackState.Finished>(engine.state.value)
        assertEquals(0.0, fixes.last().speedMetersPerSecond, 0.31)
        fixes.zipWithNext().forEach { (a, b) ->
            val jump = GeoMath.distanceMeters(a.position, b.position)
            assertTrue(jump <= a.speedMetersPerSecond * 1.05 + 2.5, "teleported $jump m in one tick")
        }
    }

    @Test
    fun `slows down for a right-angle corner`() = runTest {
        val route = rightAngleRoute(speedMps = 15.0)
        // 10 Hz ticks so a sample lands close to the corner's speed minimum
        val params = SimulationParams(jitterEnabled = false, tickHz = 10.0)
        val fixes = collectAll(SimulationEngine(route, params, testClock()))
        // Find minimum speed in fixes near the corner (middle third of playback)
        val middle = fixes.subList(fixes.size / 3, fixes.size * 2 / 3)
        val minSpeed = middle.minOf { it.speedMetersPerSecond }
        // 90° turn cap: 15 × (1 − 90/180 × 0.9) = 8.25 m/s
        assertTrue(minSpeed < 9.0, "corner speed was $minSpeed, expected < 9")
    }

    @Test
    fun `is deterministic for a fixed seed`() = runTest {
        val route = straightRoute(500.0)
        val params = SimulationParams() // jitter on
        val a = collectAll(SimulationEngine(route, params, testClock(), Random(42)))
        val b = collectAll(SimulationEngine(route, params, testClock(), Random(42)))
        assertEquals(a.size, b.size)
        a.zip(b).forEach { (x, y) -> assertEquals(x, y) }
    }

    @Test
    fun `speed multiplier shortens playback`() = runTest {
        val route = straightRoute(2000.0)
        val baselineStart = testScheduler.currentTime
        collectAll(SimulationEngine(route, noJitter, testClock()))
        val baseline = testScheduler.currentTime - baselineStart

        val fastStart = testScheduler.currentTime
        collectAll(SimulationEngine(route, noJitter, testClock(), initialSpeedMultiplier = 2.0))
        val fast = testScheduler.currentTime - fastStart

        assertTrue(fast < baseline * 0.65, "2× playback took $fast vs baseline $baseline")
    }

    @Test
    fun `jitter affects reported position but not the track`() = runTest {
        val route = straightRoute(500.0)
        val withJitter = collectAll(
            SimulationEngine(route, SimulationParams(jitterSigmaMeters = 3.0), testClock(), Random(7)),
        )
        // Reported accuracy stays within the configured band
        withJitter.forEach { fix ->
            assertTrue(fix.accuracyMeters in 3.0..8.0, "accuracy ${fix.accuracyMeters}")
        }
        // And the end point is still reached (within jitter tolerance)
        val end = route.points.last()
        assertTrue(GeoMath.distanceMeters(withJitter.last().position, end) < 15.0)
    }
}
