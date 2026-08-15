package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.remainingSecondsOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationEngineEtaTest {

    private val noJitter = SimulationParams(jitterEnabled = false)

    private fun TestScope.testClock() = SimClock { testScheduler.currentTime * 1_000_000 }

    private fun straightRoute(lengthMeters: Double, speedMps: Double = 10.0): Route {
        val start = LatLng(0.0, 0.0)
        val segments = (lengthMeters / 100.0).toInt()
        val points = (0..segments).map { GeoMath.destination(start, 90.0, 100.0 * it) }
        val distances = List(segments) { 100.0 }
        return Route(
            points = points,
            legs = listOf(RouteLeg(distances, distances.map { it / speedMps })),
            distanceMeters = lengthMeters,
            durationSeconds = lengthMeters / speedMps,
        )
    }

    @Test
    fun `remaining time starts near the route duration and counts down`() = runTest {
        val route = straightRoute(1000.0) // 100 s at 10 m/s
        val engine = SimulationEngine(route, noJitter, testClock())
        val remaining = mutableListOf<Double>()
        engine.fixes.collect {
            engine.state.value.remainingSecondsOrNull?.let { seconds -> remaining += seconds }
        }
        assertTrue(remaining.first() in 90.0..110.0, "initial ETA ${remaining.first()}")
        remaining.zipWithNext().forEach { (a, b) ->
            assertTrue(b <= a + 0.01, "ETA increased: $a -> $b")
        }
        assertTrue(remaining.last() < 5.0, "final ETA ${remaining.last()}")
    }

    @Test
    fun `traffic scale stretches playback and composes with the speed multiplier`() = runTest {
        val baselineSeconds = timeToFinish(SimulationParams(jitterEnabled = false))
        val congested = timeToFinish(SimulationParams(jitterEnabled = false, durationScale = 1.5))
        assertTrue(
            congested in (baselineSeconds * 1.3)..(baselineSeconds * 1.7),
            "expected ~1.5x of $baselineSeconds, got $congested",
        )

        val congestedFast = timeToFinish(
            SimulationParams(jitterEnabled = false, durationScale = 1.5),
            speedMultiplier = 2.0,
        )
        assertTrue(
            congestedFast < congested / 1.5,
            "multiplier should shorten the congested run: $congestedFast vs $congested",
        )
    }

    private suspend fun TestScope.timeToFinish(
        params: SimulationParams,
        speedMultiplier: Double = 1.0,
    ): Double {
        val engine = SimulationEngine(
            straightRoute(1000.0),
            params,
            testClock(),
            initialSpeedMultiplier = speedMultiplier,
        )
        val startMs = testScheduler.currentTime
        engine.fixes.collect { }
        return (testScheduler.currentTime - startMs) / 1000.0
    }

    @Test
    fun `paused state carries the frozen remaining time`() = runTest {
        val route = straightRoute(2000.0)
        val engine = SimulationEngine(route, noJitter, testClock())
        val job = launch { engine.fixes.collect { } }

        advanceTimeBy(20_000)
        val before = engine.state.value.remainingSecondsOrNull
        assertTrue(before != null && before > 0.0)
        engine.pause()
        advanceTimeBy(10_000)
        val paused = assertIs<PlaybackState.Paused>(engine.state.value)
        assertEquals(before, paused.remainingSeconds, 0.001)

        engine.stop()
        advanceTimeBy(120_000)
        job.cancel()
    }
}
