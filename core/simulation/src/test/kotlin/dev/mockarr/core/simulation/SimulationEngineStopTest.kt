package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationEngineStopTest {

    private val noJitter = SimulationParams(jitterEnabled = false)

    private fun TestScope.testClock() = SimClock { testScheduler.currentTime * 1_000_000 }

    /** 1 000 m straight east at 10 m/s, one leg, optional destination wait. */
    private fun route(destinationWait: Int = 0): Route {
        val start = LatLng(0.0, 0.0)
        val points = (0..10).map { GeoMath.destination(start, 90.0, 100.0 * it) }
        val distances = List(10) { 100.0 }
        return Route(
            points = points,
            legs = listOf(RouteLeg(distances, distances.map { it / 10.0 })),
            distanceMeters = 1_000.0,
            durationSeconds = 100.0,
            waypointWaitsSeconds = listOf(0, destinationWait),
        )
    }

    @Test
    fun `stopping mid-route flags an early stop and still finishes`() = runTest {
        val engine = SimulationEngine(route(), noJitter, testClock())
        val job = launch { engine.fixes.collect {} }
        advanceTimeBy(10_000)
        assertIs<PlaybackState.Playing>(engine.state.value)

        engine.stop()
        assertTrue(engine.stoppedBeforeArrival)
        advanceTimeBy(60_000)
        job.join()
        assertEquals(PlaybackState.Finished, engine.state.value)
    }

    @Test
    fun `running to the end is not an early stop`() = runTest {
        val engine = SimulationEngine(route(), noJitter, testClock())
        val job = launch { engine.fixes.collect {} }
        advanceTimeBy(200_000)
        job.join()
        assertFalse(engine.stoppedBeforeArrival)
    }

    @Test
    fun `stopping during the destination wait counts as arrived`() = runTest {
        val engine = SimulationEngine(route(destinationWait = 120), noJitter, testClock())
        val job = launch { engine.fixes.collect {} }
        advanceTimeBy(160_000)
        val dwelling = assertIs<PlaybackState.Dwelling>(engine.state.value)
        assertTrue(dwelling.isDestination)

        engine.stop()
        assertFalse(engine.stoppedBeforeArrival)
        advanceTimeBy(60_000)
        job.join()
    }
}
