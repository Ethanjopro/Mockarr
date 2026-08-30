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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationEngineDwellTest {

    private val noJitter = SimulationParams(jitterEnabled = false)

    private fun TestScope.testClock() = SimClock { testScheduler.currentTime * 1_000_000 }

    /**
     * Two-leg straight route east along the equator: start → mid waypoint at
     * [legMeters] → destination at 2×[legMeters], uniform segment speed.
     */
    private fun twoLegRoute(
        legMeters: Double = 500.0,
        segmentMeters: Double = 100.0,
        speedMps: Double = 10.0,
        waits: List<Int> = emptyList(),
    ): Route {
        val start = LatLng(0.0, 0.0)
        val segmentsPerLeg = (legMeters / segmentMeters).toInt()
        val points = mutableListOf(start)
        for (i in 1..segmentsPerLeg * 2) {
            points += GeoMath.destination(start, 90.0, segmentMeters * i)
        }
        val legDistances = List(segmentsPerLeg) { segmentMeters }
        val leg = RouteLeg(legDistances, legDistances.map { it / speedMps })
        return Route(
            points = points,
            legs = listOf(leg, leg),
            distanceMeters = legMeters * 2,
            durationSeconds = legMeters * 2 / speedMps,
            waypointWaitsSeconds = waits,
        )
    }

    private suspend fun collectAll(engine: SimulationEngine): List<SimulatedFix> {
        val fixes = mutableListOf<SimulatedFix>()
        engine.fixes.collect { fixes += it }
        return fixes
    }

    @Test
    fun `waits at the mid stop for the configured time`() = runTest {
        val baselineStart = testScheduler.currentTime
        collectAll(SimulationEngine(twoLegRoute(), noJitter, testClock()))
        val baseline = testScheduler.currentTime - baselineStart

        val dwellStart = testScheduler.currentTime
        val route = twoLegRoute(waits = listOf(0, 60, 0))
        val fixes = collectAll(SimulationEngine(route, noJitter, testClock()))
        val withDwell = testScheduler.currentTime - dwellStart

        val extraSeconds = (withDwell - baseline) / 1000.0
        assertTrue(extraSeconds in 55.0..65.0, "expected ≈60 s extra, got $extraSeconds")
        // The dwell fixes sit exactly on the mid waypoint at zero speed.
        val mid = route.points[route.points.size / 2]
        val stationary = fixes.filter {
            it.speedMetersPerSecond == 0.0 && GeoMath.distanceMeters(it.position, mid) < 1.0
        }
        assertTrue(stationary.size > 50, "expected many stationary fixes at the stop")
    }

    @Test
    fun `dwelling state counts down and returns to playing`() = runTest {
        val route = twoLegRoute(waits = listOf(0, 60, 0))
        val engine = SimulationEngine(route, noJitter, testClock())
        val job = launch { engine.fixes.collect {} }

        // Leg 1 is 500 m at ≤10 m/s ⇒ arrive at the stop within 80 s.
        advanceTimeBy(80_000)
        val dwelling = assertIs<PlaybackState.Dwelling>(engine.state.value)
        assertTrue(dwelling.waitSecondsLeft in 30.0..60.0, "countdown was ${dwelling.waitSecondsLeft}")
        assertEquals(1, dwelling.waypointIndex)

        advanceTimeBy(10_000)
        val later = assertIs<PlaybackState.Dwelling>(engine.state.value)
        assertTrue(later.waitSecondsLeft < dwelling.waitSecondsLeft, "countdown did not decrease")

        advanceTimeBy(60_000)
        assertTrue(engine.state.value !is PlaybackState.Dwelling, "never resumed playing")
        engine.stop()
        advanceTimeBy(60_000)
        job.join()
    }

    @Test
    fun `dwells at the destination before finishing`() = runTest {
        val route = twoLegRoute(waits = listOf(0, 0, 60))
        val engine = SimulationEngine(route, noJitter, testClock())
        val job = launch { engine.fixes.collect {} }

        // 1 000 m at ≤10 m/s ⇒ at the destination within 160 s.
        advanceTimeBy(160_000)
        val dwelling = assertIs<PlaybackState.Dwelling>(engine.state.value)
        assertEquals(2, dwelling.waypointIndex)

        advanceTimeBy(70_000)
        assertIs<PlaybackState.Finished>(engine.state.value)
        job.join()
    }

    @Test
    fun `initial eta includes the dwell time`() = runTest {
        val plain = SimulationEngine(twoLegRoute(), noJitter, testClock())
        val withWait = SimulationEngine(twoLegRoute(waits = listOf(0, 60, 0)), noJitter, testClock())
        val plainEta = (plain.state.value as PlaybackState.Playing).remainingSeconds
        val waitEta = (withWait.state.value as PlaybackState.Playing).remainingSeconds
        assertEquals(60.0, waitEta - plainEta, 1e-6)
    }

    @Test
    fun `dwell time scales with the speed multiplier`() = runTest {
        val start = testScheduler.currentTime
        collectAll(
            SimulationEngine(
                twoLegRoute(waits = listOf(0, 60, 0)),
                noJitter,
                testClock(),
                initialSpeedMultiplier = 2.0,
            ),
        )
        val at2x = testScheduler.currentTime - start

        val baselineStart = testScheduler.currentTime
        collectAll(SimulationEngine(twoLegRoute(), noJitter, testClock(), initialSpeedMultiplier = 2.0))
        val baseline = testScheduler.currentTime - baselineStart

        // Extra = 30 s scaled dwell + brake/re-accel overhead at the stop
        // (cruise is 20 m/s at 2×, physics limits are unscaled) — well under
        // the 60 s an unscaled dwell would add.
        val extraSeconds = (at2x - baseline) / 1000.0
        assertTrue(extraSeconds in 25.0..45.0, "expected ≈30–40 s extra at 2×, got $extraSeconds")
    }

    @Test
    fun `pause during dwell freezes the countdown`() = runTest {
        val route = twoLegRoute(waits = listOf(0, 60, 0))
        val engine = SimulationEngine(route, noJitter, testClock())
        val job = launch { engine.fixes.collect {} }

        advanceTimeBy(80_000)
        val beforePause = assertIs<PlaybackState.Dwelling>(engine.state.value)
        engine.pause()
        advanceTimeBy(120_000)
        assertIs<PlaybackState.Paused>(engine.state.value)

        engine.resume()
        advanceTimeBy(1_000)
        val resumed = assertIs<PlaybackState.Dwelling>(engine.state.value)
        assertEquals(1, resumed.waypointIndex)
        assertTrue(
            resumed.waitSecondsLeft >= beforePause.waitSecondsLeft - 5.0,
            "countdown ran while paused: ${beforePause.waitSecondsLeft} → ${resumed.waitSecondsLeft}",
        )
        engine.stop()
        advanceTimeBy(60_000)
        job.join()
    }

    @Test
    fun `stop during dwell finishes promptly`() = runTest {
        val route = twoLegRoute(waits = listOf(0, 3600, 0))
        val engine = SimulationEngine(route, noJitter, testClock())
        val job = launch { engine.fixes.collect {} }

        advanceTimeBy(80_000)
        assertIs<PlaybackState.Dwelling>(engine.state.value)
        engine.stop()
        advanceTimeBy(10_000)
        job.join()
        assertIs<PlaybackState.Finished>(engine.state.value)
    }

    @Test
    fun `wait on the start waypoint delays departure`() = runTest {
        val route = twoLegRoute(waits = listOf(30, 0, 0))
        val engine = SimulationEngine(route, noJitter, testClock())
        val fixes = mutableListOf<SimulatedFix>()
        val job = launch { engine.fixes.collect { fixes += it } }

        advanceTimeBy(20_000)
        assertEquals(0, assertIs<PlaybackState.Dwelling>(engine.state.value).waypointIndex)
        val origin = route.points.first()
        assertTrue(GeoMath.distanceMeters(fixes.last().position, origin) < 1.0, "left early")

        advanceTimeBy(300_000)
        job.join()
        assertIs<PlaybackState.Finished>(engine.state.value)
    }
}
