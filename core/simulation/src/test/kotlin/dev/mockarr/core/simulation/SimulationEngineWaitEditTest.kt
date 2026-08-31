package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Mid-run wait edits: [SimulationEngine.setWaypointWait] applied on the next tick. */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulationEngineWaitEditTest {

    private val noJitter = SimulationParams(jitterEnabled = false)

    private fun TestScope.testClock() = SimClock { testScheduler.currentTime * 1_000_000 }

    /**
     * Two-leg straight route east along the equator: start → mid waypoint at
     * 500 m → destination at 1 000 m, uniform 10 m/s segments (the same shape
     * SimulationEngineDwellTest drives).
     */
    private fun twoLegRoute(waits: List<Int> = emptyList()): Route {
        val start = LatLng(0.0, 0.0)
        val points = (0..10).map { GeoMath.destination(start, 90.0, 100.0 * it) }
        val legDistances = List(5) { 100.0 }
        val leg = RouteLeg(legDistances, legDistances.map { it / 10.0 })
        return Route(
            points = points,
            legs = listOf(leg, leg),
            distanceMeters = 1000.0,
            durationSeconds = 100.0,
            waypointWaitsSeconds = waits,
        )
    }

    /** Runs [engine] to completion (with [edit] fired at virtual [editAtMillis]) and returns the run's millis. */
    private suspend fun TestScope.timeToFinish(engine: SimulationEngine, editAtMillis: Long, edit: () -> Unit): Long {
        val start = testScheduler.currentTime
        launch {
            delay(editAtMillis)
            edit()
        }
        engine.fixes.collect {}
        return testScheduler.currentTime - start
    }

    @Test
    fun `raising a wait ahead extends the drive by the difference`() = runTest {
        val base = SimulationEngine(twoLegRoute(waits = listOf(0, 60, 0)), noJitter, testClock())
        val baseline = timeToFinish(base, editAtMillis = 10_000) {}

        val edited = SimulationEngine(twoLegRoute(waits = listOf(0, 60, 0)), noJitter, testClock())
        // Fires at 10 s: still on leg 1, well before the 500 m stop.
        val withEdit = timeToFinish(edited, editAtMillis = 10_000) { edited.setWaypointWait(1, 120) }

        val extraSeconds = (withEdit - baseline) / 1000.0
        assertTrue(extraSeconds in 55.0..65.0, "expected ≈60 s more, got $extraSeconds")
    }

    @Test
    fun `clearing a wait ahead removes the dwell`() = runTest {
        val base = SimulationEngine(twoLegRoute(), noJitter, testClock())
        val baseline = timeToFinish(base, editAtMillis = 10_000) {}

        val edited = SimulationEngine(twoLegRoute(waits = listOf(0, 300, 0)), noJitter, testClock())
        val withEdit = timeToFinish(edited, editAtMillis = 10_000) { edited.setWaypointWait(1, 0) }

        // The cleared stop no longer dwells — though the engine still brakes to
        // rest there (its vertex speed was pinned at 0 when the route was built).
        val extraSeconds = (withEdit - baseline) / 1000.0
        assertTrue(extraSeconds < 30.0, "expected no dwell after clearing, got $extraSeconds s extra")
    }

    @Test
    fun `adding a wait to an unwaited stop dwells there`() = runTest {
        val engine = SimulationEngine(twoLegRoute(), noJitter, testClock())
        val job = launch { engine.fixes.collect {} }
        advanceTimeBy(10_000)
        engine.setWaypointWait(1, 60)

        advanceTimeBy(70_000) // arrival at the mid stop is ~55 s in
        val dwelling = assertIs<PlaybackState.Dwelling>(engine.state.value)
        assertEquals(1, dwelling.waypointIndex)
        engine.stop()
        advanceTimeBy(60_000)
        job.join()
    }

    @Test
    fun `shortening the active dwell resumes early`() = runTest {
        val engine = SimulationEngine(twoLegRoute(waits = listOf(0, 600, 0)), noJitter, testClock())
        val job = launch { engine.fixes.collect {} }
        advanceTimeBy(80_000)
        assertIs<PlaybackState.Dwelling>(engine.state.value)

        engine.setWaypointWait(1, 30) // at most a few seconds beyond what's already been waited
        advanceTimeBy(60_000)
        assertTrue(engine.state.value !is PlaybackState.Dwelling, "still dwelling after the cut")
        advanceTimeBy(600_000)
        job.join()
        assertIs<PlaybackState.Finished>(engine.state.value)
    }

    @Test
    fun `extending the active dwell keeps waiting`() = runTest {
        val engine = SimulationEngine(twoLegRoute(waits = listOf(0, 60, 0)), noJitter, testClock())
        val job = launch { engine.fixes.collect {} }
        advanceTimeBy(80_000) // arrival at the stop is within 80 s; its 60 s dwell is still running
        val before = assertIs<PlaybackState.Dwelling>(engine.state.value)

        engine.setWaypointWait(1, 600)
        advanceTimeBy(120_000)
        val still = assertIs<PlaybackState.Dwelling>(engine.state.value)
        assertEquals(1, still.waypointIndex)
        assertTrue(still.waitSecondsLeft > before.waitSecondsLeft, "countdown did not extend")
        engine.stop()
        advanceTimeBy(60_000)
        job.join()
    }

    @Test
    fun `editing a stop already passed is a no-op`() = runTest {
        val engine = SimulationEngine(twoLegRoute(waits = listOf(0, 5, 0)), noJitter, testClock())
        val job = launch { engine.fixes.collect {} }
        advanceTimeBy(90_000) // the 5 s mid dwell is long over
        assertIs<PlaybackState.Playing>(engine.state.value)

        engine.setWaypointWait(1, 3600)
        advanceTimeBy(120_000)
        job.join()
        assertIs<PlaybackState.Finished>(engine.state.value)
    }
}
