package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.PlaybackState
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Picking a drive back up mid-route via [SimulationEngine.ResumePoint]. */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulationEngineResumeTest {

    private val noJitter = SimulationParams(jitterEnabled = false)

    private fun TestScope.testClock() = SimClock { testScheduler.currentTime * 1_000_000 }

    /** Start → mid stop at 500 m → destination at 1 000 m, uniform 10 m/s. */
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

    private suspend fun TestScope.runToEnd(engine: SimulationEngine): Long {
        val start = testScheduler.currentTime
        engine.fixes.collect {}
        return testScheduler.currentTime - start
    }

    @Test
    fun `no resume point is the ordinary run`() = runTest {
        val plain = SimulationEngine(twoLegRoute(waits = listOf(0, 30, 0)), noJitter, testClock())
        val explicit = SimulationEngine(
            twoLegRoute(waits = listOf(0, 30, 0)),
            noJitter,
            testClock(),
            resumeFrom = null,
        )
        assertEquals(plain.state.value, explicit.state.value)
        assertEquals(0.0, explicit.distanceMeters)
    }

    @Test
    fun `resuming halfway starts at that distance and finishes the route`() = runTest {
        val engine = SimulationEngine(
            twoLegRoute(),
            noJitter,
            testClock(),
            resumeFrom = SimulationEngine.ResumePoint(distanceMeters = 500.0),
        )
        val first = engine.state.value
        assertIs<PlaybackState.Playing>(first)
        assertEquals(0.5, first.progress, 1e-6)
        // Half the cruise time is left (plus the acceleration from rest).
        assertTrue(first.remainingSeconds in 50.0..60.0, "remaining ${first.remainingSeconds}")

        var fixes = 0
        engine.fixes.collect { fixes++ }
        assertEquals(1000.0, engine.distanceMeters, 0.01)
        assertTrue(fixes < 100, "a half route should not take a full route's ticks ($fixes)")
    }

    @Test
    fun `stops already driven past are not dwelt at again`() = runTest {
        val waits = listOf(0, 600, 0) // a ten-minute wait at the mid stop
        val resumed = SimulationEngine(
            twoLegRoute(waits),
            noJitter,
            testClock(),
            resumeFrom = SimulationEngine.ResumePoint(distanceMeters = 700.0),
        )
        assertTrue(
            (resumed.state.value as PlaybackState.Playing).remainingSeconds < 100.0,
            "the passed wait must not count towards time left",
        )
        val millis = runToEnd(resumed)
        assertTrue(millis < 100_000, "drove past the stop yet waited: $millis ms")
    }

    @Test
    fun `a wait in progress resumes with its remaining seconds`() = runTest {
        val waits = listOf(0, 120, 0)
        val engine = SimulationEngine(
            twoLegRoute(waits),
            noJitter,
            testClock(),
            resumeFrom = SimulationEngine.ResumePoint(distanceMeters = 500.0, dwellSecondsLeft = 20.0),
        )
        val first = engine.state.value
        assertIs<PlaybackState.Dwelling>(first)
        assertEquals(1, first.waypointIndex)
        assertEquals(20.0, first.waitSecondsLeft, 1e-6)
        assertEquals(20.0, engine.activeDwellSecondsLeft, 1e-6)

        val millis = runToEnd(engine)
        // 20 s of wait, then ~50 s of driving — never the full 120 s wait again.
        assertTrue(millis in 65_000..95_000, "took $millis ms")
    }

    @Test
    fun `resuming at the destination finishes at once`() = runTest {
        val engine = SimulationEngine(
            twoLegRoute(),
            noJitter,
            testClock(),
            resumeFrom = SimulationEngine.ResumePoint(distanceMeters = 5000.0),
        )
        val millis = runToEnd(engine)
        assertTrue(millis <= 2_000, "took $millis ms")
        assertIs<PlaybackState.Finished>(engine.state.value)
    }
}
