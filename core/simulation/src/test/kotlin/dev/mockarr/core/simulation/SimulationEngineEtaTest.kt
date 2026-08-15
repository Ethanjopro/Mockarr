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
