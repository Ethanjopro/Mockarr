package dev.mockarr.core.simulation

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.OffRoadSpan
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.remainingSecondsOrNull
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class DriveEstimateTest {

    /** Two legs east: 600 m at 12 m/s, then 400 m at 8 m/s, with a wait at the middle stop. */
    private fun twoLegRoute(): Route {
        val start = LatLng(32.78, -96.80)
        val points = (0..10).map { GeoMath.destination(start, 90.0, 100.0 * it) }
        return Route(
            points = points,
            legs = listOf(
                RouteLeg(List(6) { 100.0 }, List(6) { 100.0 / 12.0 }),
                RouteLeg(List(4) { 100.0 }, List(4) { 100.0 / 8.0 }),
            ),
            distanceMeters = 1000.0,
            durationSeconds = 600.0 / 12.0 + 400.0 / 8.0,
            waypointWaitsSeconds = listOf(0, 90, 0),
            offRoadSpans = listOf(OffRoadSpan(8, 10)),
        )
    }

    @Test
    fun `the estimate is cruise time plus waits`() {
        val estimate = estimatedDriveSeconds(twoLegRoute(), SimulationParams(jitterEnabled = false))

        assertEquals(50.0 + 50.0 + 90.0, estimate, 1.0)
    }

    @Test
    fun `traffic and off-road pauses count, as the drive counts them`() {
        val params = SimulationParams(jitterEnabled = false, durationScale = 1.5, offRoadPauseSeconds = 2)

        val estimate = estimatedDriveSeconds(twoLegRoute(), params)

        assertEquals(100.0 * 1.5 + 90.0 + 2.0, estimate, 1.0)
    }

    @Test
    fun `a drive opens on the estimate even with its random speed spread`() {
        val params = SimulationParams(
            jitterEnabled = false,
            durationScale = 1.3,
            speedVarianceFraction = 0.08,
            offRoadPauseSeconds = 2,
        )
        val route = twoLegRoute()
        val engine = SimulationEngine(route, params, SimClock { 0L }, random = Random(11))

        val opening = engine.state.value.remainingSecondsOrNull ?: error("no ETA at start")

        assertEquals(estimatedDriveSeconds(route, params), opening, 0.5)
    }
}
