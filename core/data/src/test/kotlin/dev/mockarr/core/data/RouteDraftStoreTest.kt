package dev.mockarr.core.data

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.Waypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteDraftStoreTest {

    private val dir: File = Files.createTempDirectory("draft").toFile()
    private val store = RouteDraftStore(File(dir, RouteDraftStore.FILE_NAME), Dispatchers.Unconfined)

    private val draft = RouteDraft(
        waypoints = listOf(
            Waypoint(LatLng(32.7753, -96.8089), name = "Reunion Tower"),
            Waypoint(LatLng(32.7873, -96.8005), waitSeconds = 60),
        ),
        profile = RoutingProfile.WALKING,
        route = Route(
            points = listOf(LatLng(32.7753, -96.8089), LatLng(32.7873, -96.8005)),
            legs = listOf(RouteLeg(listOf(1400.0), listOf(1000.0))),
            distanceMeters = 1400.0,
            durationSeconds = 1000.0,
        ),
        routedFor = listOf(LatLng(32.7753, -96.8089), LatLng(32.7873, -96.8005)),
        routeSaved = true,
    )

    @Test
    fun `a written draft reads back whole`() = runTest {
        store.write(draft).join()
        assertEquals(draft, store.read())
    }

    @Test
    fun `clearing leaves nothing to restore`() = runTest {
        store.write(draft).join()
        store.clear().join()
        assertNull(store.read())
    }

    @Test
    fun `an unreadable file reads as no draft`() = runTest {
        File(dir, RouteDraftStore.FILE_NAME).writeText("{ not json")
        assertNull(store.read())
    }
}
