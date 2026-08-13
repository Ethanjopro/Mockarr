package dev.mockarr.core.mocklocation

import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import dev.mockarr.core.model.SimulatedFix

/**
 * Feeds fixes into the platform's test providers. Requires Mockarr to be the
 * selected mock location app in Developer Options; [start] reports
 * [MockStartResult.NotSelectedAsMockApp] otherwise.
 *
 * Mocks gps + network, plus fused on API 31+. Play services' fused provider
 * honors platform test providers, so consumers like Google Maps follow along.
 */
class AndroidMockLocationController(
    private val locationManager: LocationManager,
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

    override fun start(): MockStartResult =
        if (isRunning) MockStartResult.Ok else registerProviders()

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
        addTestProvider(provider)
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
        for (provider in activeProviders) {
            val location = Location(provider).apply {
                latitude = fix.position.latitude
                longitude = fix.position.longitude
                altitude = fix.altitudeMeters
                speed = fix.speedMetersPerSecond.toFloat()
                bearing = fix.bearingDegrees.toFloat()
                // Mandatory: fixes without accuracy/time/elapsedRealtimeNanos are dropped.
                accuracy = fix.accuracyMeters.toFloat()
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                bearingAccuracyDegrees = BEARING_ACCURACY_DEGREES
                speedAccuracyMetersPerSecond = SPEED_ACCURACY_MPS
                verticalAccuracyMeters = VERTICAL_ACCURACY_METERS
            }
            try {
                locationManager.setTestProviderLocation(provider, location)
            } catch (_: SecurityException) {
                // Mock app selection revoked mid-session; stop() cleans up what it can.
            } catch (_: IllegalArgumentException) {
                // Provider vanished (OEM quirk); skip this tick for it.
            }
        }
    }

    override fun stop() {
        activeProviders.forEach(::removeQuietly)
        activeProviders = emptyList()
    }

    // Positional args (Java API): requiresNetwork, requiresSatellite, requiresCell,
    // hasMonetaryCost, supportsAltitude, supportsSpeed, supportsBearing.
    // ProviderProperties constants are compile-time-inlined ints, safe below API 31.
    private fun addTestProvider(provider: String) {
        val requiresSatellite = provider == LocationManager.GPS_PROVIDER
        locationManager.addTestProvider(
            provider,
            false,
            requiresSatellite,
            false,
            false,
            true,
            true,
            true,
            ProviderProperties.POWER_USAGE_LOW,
            ProviderProperties.ACCURACY_FINE,
        )
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

    private companion object {
        const val BEARING_ACCURACY_DEGREES = 10f
        const val SPEED_ACCURACY_MPS = 0.5f
        const val VERTICAL_ACCURACY_METERS = 5f
    }
}
