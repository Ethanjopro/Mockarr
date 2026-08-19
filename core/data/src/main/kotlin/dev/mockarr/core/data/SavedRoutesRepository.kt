package dev.mockarr.core.data

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.routing.Polyline6
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class SavedRoutesRepository(
    private val dao: SavedRouteDao,
    private val json: Json = Json,
) {

    fun observeAll(): Flow<List<SavedRouteEntity>> = dao.observeAll()

    suspend fun save(name: String, route: Route, profile: RoutingProfile, nowEpochMillis: Long): Long =
        dao.insert(
            SavedRouteEntity(
                name = name,
                createdAtEpochMillis = nowEpochMillis,
                profile = profile.name,
                distanceMeters = route.distanceMeters,
                durationSeconds = route.durationSeconds,
                encodedPolyline6 = Polyline6.encode(route.points),
                legsJson = json.encodeToString(route.legs),
                altitudesJson = route.altitudes?.let { json.encodeToString(it) },
                waypointsJson = route.snappedWaypoints
                    .takeIf { it.isNotEmpty() }
                    ?.let { json.encodeToString(it) },
                waypointWaitsJson = route.waypointWaitsSeconds
                    .takeIf { waits -> waits.any { it > 0 } }
                    ?.let { json.encodeToString(it) },
            ),
        )

    suspend fun delete(entity: SavedRouteEntity) = dao.delete(entity)

    /** Re-insert after an undone delete (a fresh id is fine). */
    suspend fun restore(entity: SavedRouteEntity): Long = dao.insert(entity.copy(id = 0))

    fun toRoute(entity: SavedRouteEntity): Route {
        val points = Polyline6.decode(entity.encodedPolyline6)
        val altitudes = entity.altitudesJson
            ?.let { runCatching { json.decodeFromString<List<Double>>(it) }.getOrNull() }
            ?.takeIf { it.size == points.size }
        val waypoints = entity.waypointsJson
            ?.let { runCatching { json.decodeFromString<List<LatLng>>(it) }.getOrNull() }
            .orEmpty()
        val waits = entity.waypointWaitsJson
            ?.let { runCatching { json.decodeFromString<List<Int>>(it) }.getOrNull() }
            ?.takeIf { it.size == waypoints.size }
            .orEmpty()
        return Route(
            points = points,
            legs = json.decodeFromString<List<RouteLeg>>(entity.legsJson),
            distanceMeters = entity.distanceMeters,
            durationSeconds = entity.durationSeconds,
            altitudes = altitudes,
            snappedWaypoints = waypoints,
            waypointWaitsSeconds = waits,
        )
    }

    fun profileOf(entity: SavedRouteEntity): RoutingProfile =
        RoutingProfile.fromNameOrDefault(entity.profile)
}
