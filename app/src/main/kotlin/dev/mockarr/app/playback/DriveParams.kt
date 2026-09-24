package dev.mockarr.app.playback

import dev.mockarr.core.data.MockarrSettings
import dev.mockarr.core.model.Route
import dev.mockarr.core.simulation.SimulationParams
import dev.mockarr.core.simulation.TrafficModel
import dev.mockarr.core.simulation.estimatedDriveSeconds
import java.time.LocalDateTime

/**
 * A drive's tuning, from the settings: the one recipe shared by the service's engine
 * and every duration quoted before a drive (the saved list, the stat card), so those
 * numbers are the Time left the drive opens on (critique, session 42: 9 / 10 / 11 min).
 */
internal fun driveParams(settings: MockarrSettings, trafficFactor: Double): SimulationParams = SimulationParams(
    tickHz = settings.tickHz,
    durationScale = trafficFactor,
    speedVarianceFraction = SPEED_VARIANCE_FRACTION,
    offRoadPauseSeconds = if (settings.offRoadWalkEnabled) OFF_ROAD_PAUSE_SECONDS else 0,
    jitterEnabled = settings.jitterEnabled,
    jitterSigmaMeters = settings.jitterSigmaMeters,
)

/** Rush-hour congestion for a drive starting at [now]; 1.0 with the setting off. */
internal fun trafficFactorAt(settings: MockarrSettings, now: LocalDateTime = LocalDateTime.now()): Double =
    if (settings.trafficSimEnabled) TrafficModel.congestionFactor(now.dayOfWeek, now.hour, now.minute) else 1.0

/** How long a drive of [route] takes, worked out exactly as the drive will work it out. */
internal fun plannedDriveSeconds(route: Route, settings: MockarrSettings, trafficFactor: Double): Double =
    estimatedDriveSeconds(route, driveParams(settings, trafficFactor))

private const val SPEED_VARIANCE_FRACTION = 0.08
private const val OFF_ROAD_PAUSE_SECONDS = 2
