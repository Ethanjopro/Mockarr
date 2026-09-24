package dev.mockarr.core.simulation

import dev.mockarr.core.model.Route

/**
 * The drive's own duration, quoted before it starts: cruise time at the engine's
 * segment speeds (traffic scale and speed clamps included) plus every dwell, waits
 * and off-road pauses alike. It is exactly the Time left a drive of [route] opens
 * with at 1×, so the saved list, the stat card and the drive itself agree.
 */
fun estimatedDriveSeconds(route: Route, params: SimulationParams): Double {
    if (route.points.size < 2) return 0.0
    val geometry = RouteGeometry(
        route,
        params.decelerationMps2,
        params.durationScale,
        offRoadPauseSeconds = params.offRoadPauseSeconds,
    )
    return geometry.totalDurationSeconds + geometry.dwellStops.sumOf { it.waitSeconds }
}
