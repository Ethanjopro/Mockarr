package dev.mockarr.app.data

import dev.mockarr.core.data.SavedRouteDao
import dev.mockarr.core.data.SavedRouteEntity
import dev.mockarr.core.data.SavedRoutesRepository
import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.OffRoadSpan
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedRouteSpansTest {

    /** Captures the inserted entity; the queries are unused by these tests. */
    private class RecordingDao : SavedRouteDao {
        var inserted: SavedRouteEntity? = null

        override fun observeAll(): Flow<List<SavedRouteEntity>> = emptyFlow()

        override suspend fun insert(route: SavedRouteEntity): Long {
            inserted = route
            return 1
        }

        override suspend fun update(route: SavedRouteEntity) = Unit

        override suspend fun delete(route: SavedRouteEntity) = Unit
    }

    private fun stitchedRoute(): Route {
        val start = LatLng(48.0, 2.0)
        val points = (0..4).map { GeoMath.destination(start, 90.0, 100.0 * it) }
        val distances = List(4) { 100.0 }
        return Route(
            points = points,
            legs = listOf(RouteLeg(distances, distances.map { it / 10.0 })),
            distanceMeters = 400.0,
            durationSeconds = 40.0,
            snappedWaypoints = listOf(points.first(), points.last()),
            offRoadSpans = listOf(OffRoadSpan(2, 4)),
        )
    }

    @Test
    fun `off-road spans survive a save and load round trip`() = runBlocking<Unit> {
        val dao = RecordingDao()
        val repository = SavedRoutesRepository(dao)

        repository.save("test", stitchedRoute(), RoutingProfile.DRIVING, nowEpochMillis = 0)
        val restored = repository.toRoute(dao.inserted!!)

        assertEquals(listOf(OffRoadSpan(2, 4)), restored.offRoadSpans)
    }

    @Test
    fun `a pre-v4 row without spans loads with none`() = runBlocking<Unit> {
        val dao = RecordingDao()
        val repository = SavedRoutesRepository(dao)

        repository.save("test", stitchedRoute().copy(offRoadSpans = emptyList()), RoutingProfile.DRIVING, 0)
        val entity = dao.inserted!!

        assertEquals(null, entity.offRoadSpansJson)
        assertTrue(repository.toRoute(entity).offRoadSpans.isEmpty())
    }
}
