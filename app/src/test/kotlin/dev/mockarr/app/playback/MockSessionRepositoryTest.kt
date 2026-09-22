package dev.mockarr.app.playback

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.simulation.SimulationEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MockSessionRepositoryTest {

    private val route = Route(
        points = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.01)),
        legs = listOf(RouteLeg(listOf(1_113.0), listOf(111.3))),
        distanceMeters = 1_113.0,
        durationSeconds = 111.3,
    )
    private val drive = MockSessionRepository.LiveDrive(route, RoutingProfile.DRIVING, saved = true)

    private fun playing() = MockSessionRepository().apply { playingStarted(SimulationEngine(route), drive) }

    @Test
    fun `publishes the drive in flight while playing`() {
        assertEquals(drive, playing().liveDrive.value)
    }

    @Test
    fun `forgets the drive when the engine ends`() {
        val repository = playing()
        repository.engineEnded()
        assertNull(repository.liveDrive.value)
    }

    @Test
    fun `a drive that was never stopped is an arrival`() {
        val repository = playing()
        repository.engineEnded()
        assertEquals(DriveOutcome.ARRIVED, repository.driveOutcome.value)
    }

    @Test
    fun `a stop request makes it a stop, even unseen by the UI`() {
        // Stop pressed in the notification while the app was away.
        val repository = playing()
        repository.stop()
        repository.engineEnded()
        assertEquals(DriveOutcome.STOPPED, repository.driveOutcome.value)
    }

    @Test
    fun `a release mid-drive is a stop`() {
        val repository = playing()
        repository.sessionReleased()
        assertEquals(DriveOutcome.STOPPED, repository.driveOutcome.value)
    }

    @Test
    fun `a release after an arrival keeps the arrival`() {
        val repository = playing()
        repository.engineEnded()
        repository.sessionReleased()
        assertEquals(DriveOutcome.ARRIVED, repository.driveOutcome.value)
    }

    @Test
    fun `a new drive clears the last outcome`() {
        val repository = playing()
        repository.stop()
        repository.engineEnded()
        repository.playingStarted(SimulationEngine(route), drive)
        assertNull(repository.driveOutcome.value)
        repository.engineEnded()
        assertEquals(DriveOutcome.ARRIVED, repository.driveOutcome.value)
    }
}
