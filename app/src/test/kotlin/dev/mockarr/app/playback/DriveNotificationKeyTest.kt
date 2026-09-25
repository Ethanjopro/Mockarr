package dev.mockarr.app.playback

import dev.mockarr.app.ui.DurationParts
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DriveNotificationKeyTest {

    private val km = DistanceUnits.KILOMETERS

    private fun keyAt(progress: Double, remainingSeconds: Double = 600.0, routeMeters: Double = 10_000.0) =
        driveNotificationKey(PlaybackState.Playing(progress, remainingSeconds), routeMeters, km)

    @Test
    fun `the bar key moves with each tenth of a percent`() {
        // Session 40: whole-percent keys moved a 2 h drive's bar once a minute.
        assertNotEquals(keyAt(0.5000)?.permille, keyAt(0.5011)?.permille)
        assertEquals(500, keyAt(0.5004)?.permille)
    }

    @Test
    fun `time left keys on whole minutes above a minute`() {
        assertEquals(keyAt(0.5, remainingSeconds = 541.0)?.timeLeft, keyAt(0.5, remainingSeconds = 580.0)?.timeLeft)
        assertEquals(DurationParts(seconds = null, hours = 0, minutes = 10), keyAt(0.5, 541.0)?.timeLeft)
    }

    @Test
    fun `time left keys on whole seconds in the last minute`() {
        assertNotEquals(keyAt(0.99, remainingSeconds = 42.0)?.timeLeft, keyAt(0.99, remainingSeconds = 41.0)?.timeLeft)
    }

    @Test
    fun `paused and waiting key apart from moving`() {
        val moving = driveNotificationKey(PlaybackState.Playing(0.5, 300.0), 10_000.0, km)
        val paused = driveNotificationKey(PlaybackState.Paused(0.5, 300.0), 10_000.0, km)
        val waiting = driveNotificationKey(PlaybackState.Dwelling(0.5, 300.0), 10_000.0, km)
        assertEquals(DriveNotificationKey.Phase.MOVING, moving?.phase)
        assertEquals(DriveNotificationKey.Phase.PAUSED, paused?.phase)
        assertEquals(DriveNotificationKey.Phase.WAITING, waiting?.phase)
    }

    @Test
    fun `stopping and finished have no key so the bar never flashes empty`() {
        assertNull(driveNotificationKey(PlaybackState.Stopping(0.5), 10_000.0, km))
        assertNull(driveNotificationKey(PlaybackState.Finished, 10_000.0, km))
        assertNull(driveNotificationKey(null, 10_000.0, km))
    }

    @Test
    fun `long routes key on each tenth of a unit driven`() {
        // 300 km: one per-mille step is 300 m, coarser than the 0.1 km the text prints.
        val route = 300_000.0
        val a = keyAt(0.5000, routeMeters = route)
        val b = keyAt(0.5004, routeMeters = route) // +120 m, same per-mille
        assertEquals(a?.permille, b?.permille)
        assertNotEquals(a?.drivenTenths, b?.drivenTenths)
    }
}
