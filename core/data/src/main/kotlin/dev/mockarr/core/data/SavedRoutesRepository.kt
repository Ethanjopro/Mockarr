package dev.mockarr.core.data

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
            ),
        )

    suspend fun delete(entity: SavedRouteEntity) = dao.delete(entity)

    /** Re-insert after an undone delete (a fresh id is fine). */
    suspend fun restore(entity: SavedRouteEntity): Long = dao.insert(entity.copy(id = 0))

    fun toRoute(entity: SavedRouteEntity): Route = Route(
        points = Polyline6.decode(entity.encodedPolyline6),
        legs = json.decodeFromString<List<RouteLeg>>(entity.legsJson),
        distanceMeters = entity.distanceMeters,
        durationSeconds = entity.durationSeconds,
    )

    fun profileOf(entity: SavedRouteEntity): RoutingProfile =
        RoutingProfile.fromNameOrDefault(entity.profile)
}
