package dev.mockarr.core.data

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.model.SessionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionSnapshotStoreTest {

    private val dir: File = Files.createTempDirectory("snapshot").toFile()
    private val store = SessionSnapshotStore(File(dir, SessionSnapshotStore.FILE_NAME), Dispatchers.Unconfined)

    private val route = Route(
        points = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.01)),
        legs = listOf(RouteLeg(listOf(1113.0), listOf(111.3))),
        distanceMeters = 1113.0,
        durationSeconds = 111.3,
        waypointWaitsSeconds = listOf(0, 60),
    )

    private fun playing(savedAt: Long) = SessionSnapshot(
        kind = SessionSnapshot.Kind.PLAYING,
        route = route,
        profile = RoutingProfile.CYCLING,
        speedMultiplier = 2.0,
        distanceMeters = 500.0,
        dwellSecondsLeft = 12.5,
        savedAtEpochMillis = savedAt,
    )

    @Test
    fun `round-trips a playing snapshot`() = runTest {
        store.write(playing(savedAt = 1_000))
        val back = store.read(nowMillis = 2_000)
        assertEquals(playing(savedAt = 1_000), back)
        assertEquals(500.0 / 1113.0, assertNotNull(back).progress, 1e-9)
    }

    @Test
    fun `round-trips a hold snapshot`() = runTest {
        val hold = SessionSnapshot(
            kind = SessionSnapshot.Kind.HOLDING,
            holdPosition = LatLng(37.0, -122.0),
            savedAtEpochMillis = 5,
        )
        store.write(hold)
        assertEquals(hold, store.read(nowMillis = 5))
    }

    @Test
    fun `reads nothing when no snapshot exists`() = runTest {
        assertNull(store.read(nowMillis = 0))
    }

    @Test
    fun `a stale snapshot reads as absent`() = runTest {
        store.write(playing(savedAt = 0))
        assertNull(store.read(nowMillis = SessionSnapshotStore.RESUME_WINDOW_MILLIS + 1))
        assertNotNull(store.read(nowMillis = SessionSnapshotStore.RESUME_WINDOW_MILLIS))
    }

    @Test
    fun `a snapshot from the future reads as absent`() = runTest {
        store.write(playing(savedAt = 10_000))
        assertNull(store.read(nowMillis = 9_000))
    }

    @Test
    fun `clear removes the file`() = runTest {
        store.write(playing(savedAt = 1))
        store.clear()
        assertFalse(File(dir, SessionSnapshotStore.FILE_NAME).exists())
        assertNull(store.read(nowMillis = 1))
    }

    @Test
    fun `garbage on disk reads as absent`() = runTest {
        File(dir, SessionSnapshotStore.FILE_NAME).writeText("{not json")
        assertNull(store.read(nowMillis = 1))
    }
}
