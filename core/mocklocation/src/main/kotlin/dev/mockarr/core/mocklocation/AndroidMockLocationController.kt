package dev.mockarr.core.mocklocation

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import dev.mockarr.core.model.SimulatedFix

/**
 * Feeds fixes into the platform's test providers and into Google Play services' fused
 * location. Requires Mockarr to be the selected mock location app in Developer Options;
 * [start] reports [MockStartResult.NotSelectedAsMockApp] otherwise.
 *
 * Mocks gps + network, plus fused on API 31+, for apps that read `LocationManager`. Play
 * services' fused location (Google Maps and most apps) is a separate engine that ignores
 * those and blends in real Wi-Fi/cell positioning: on a real phone the true location won
 * within seconds (ADR 0005). [playServices] puts that engine in mock mode for the session;
 * null (no Play services) leaves the test providers alone, as before.
 */
class AndroidMockLocationController(
    private val locationManager: LocationManager,
    private val playServices: PlayServicesMock? = null,
) : MockLocationController {

    private val providers: List<String> = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(LocationManager.FUSED_PROVIDER)
        }
    }

    private var activeProviders: List<String> = emptyList()

    override val isRunning: Boolean
        get() = activeProviders.isNotEmpty()

    override fun start(): MockStartResult {
        val result = if (isRunning) MockStartResult.Ok else registerProviders()
        // Re-armed on every start (idempotent): a hold becoming a drive also re-asserts it,
        // in case Play services restarted and dropped mock mode.
        if (result == MockStartResult.Ok) playServices?.start()
        return result
    }

    private fun registerProviders(): MockStartResult {
        val added = mutableListOf<String>()
        var failure: MockStartResult? = null
        for (provider in providers) {
            failure = tryRegister(provider, added)
            if (failure != null) break
        }
        return if (failure != null) {
            added.forEach(::removeQuietly)
            failure
        } else {
            activeProviders = added
            MockStartResult.Ok
        }
    }

    /** Returns null on success; a failure result only when the session cannot proceed. */
    private fun tryRegister(provider: String, added: MutableList<String>): MockStartResult? = try {
        removeQuietly(provider)
        locationManager.registerTestProvider(
            provider,
            requiresSatellite = provider == LocationManager.GPS_PROVIDER,
            supportsMotion = true,
        )
        locationManager.setTestProviderEnabled(provider, true)
        added += provider
        null
    } catch (_: SecurityException) {
        MockStartResult.NotSelectedAsMockApp
    } catch (e: IllegalArgumentException) {
        if (provider == LocationManager.GPS_PROVIDER) {
            MockStartResult.ProviderError(e.message ?: "Could not register test provider")
        } else {
            // Some OEMs reject individual providers; gps alone is enough to work.
            null
        }
    }

    override fun push(fix: SimulatedFix) {
        val time = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtimeNanos()
        for (provider in activeProviders) {
            try {
                locationManager.setTestProviderLocation(provider, fix.toLocation(provider, time, elapsed))
            } catch (_: SecurityException) {
                // Mock app selection revoked mid-session; stop() cleans up what it can.
            } catch (_: IllegalArgumentException) {
                // Provider vanished (OEM quirk); skip this tick for it.
            }
        }
        // The same fix, same instant, for Play services' fused engine.
        playServices?.push(fix.toLocation(PLAY_SERVICES_PROVIDER, time, elapsed))
    }

    /**
     * Sweeps every provider, not just the ones this instance registered: a
     * process killed mid-session (force stop, OOM) leaves its test providers
     * behind, and the next process must be able to remove them.
     */
    override fun stop() {
        providers.forEach(::removeQuietly)
        activeProviders = emptyList()
        // Hands Play services' fused engine back its real sources, with the providers.
        playServices?.stop()
    }

    /** Stale enabled test providers freeze the device's real location — always clean up. */
    private fun removeQuietly(provider: String) {
        try {
            locationManager.setTestProviderEnabled(provider, false)
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
        try {
            locationManager.removeTestProvider(provider)
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    /**
     * One fix as a platform [Location], identical for every provider it goes to.
     * Mandatory: fixes without accuracy / time / elapsedRealtimeNanos are dropped.
     */
    private fun SimulatedFix.toLocation(provider: String, timeMillis: Long, elapsedNanos: Long) =
        Location(provider).apply {
            latitude = position.latitude
            longitude = position.longitude
            altitude = altitudeMeters
            speed = speedMetersPerSecond.toFloat()
            bearing = bearingDegrees.toFloat()
            accuracy = accuracyMeters.toFloat()
            time = timeMillis
            elapsedRealtimeNanos = elapsedNanos
            bearingAccuracyDegrees = BEARING_ACCURACY_DEGREES
            speedAccuracyMetersPerSecond = SPEED_ACCURACY_MPS
            verticalAccuracyMeters = VERTICAL_ACCURACY_METERS
        }

    private companion object {
        /** The provider name the fused fix carries ("fused", valid on every API level). */
        const val PLAY_SERVICES_PROVIDER = "fused"
        const val BEARING_ACCURACY_DEGREES = 10f
        const val SPEED_ACCURACY_MPS = 0.5f
        const val VERTICAL_ACCURACY_METERS = 5f
    }
}
