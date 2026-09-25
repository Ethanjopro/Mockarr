package dev.mockarr.core.mocklocation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task

/**
 * Google Play services' fused location in mock mode (ADR 0005). Google Maps and most apps
 * read this engine, not `LocationManager`; it blends its own Wi-Fi and cell positioning,
 * so on a real phone the real location won a few seconds after Mockarr left the screen.
 * While mock mode is on it reports only what [push] sets.
 *
 * Every call is fire-and-forget: a failure (Play services missing, updating, or the mock
 * app selection revoked) leaves the session running on the platform test providers alone,
 * as before this existed. Mock mode needs the same "Select mock location app" setting and
 * the location permission the session already holds.
 */
@SuppressLint("MissingPermission") // Sessions start only after the permission gate.
class PlayServicesMock private constructor(private val client: FusedLocationProviderClient) {

    /** One log line per failing session, not one per second. */
    private var failureLogged = false

    fun start() {
        failureLogged = false
        call { client.setMockMode(true) }
    }

    fun push(location: Location) = call { client.setMockLocation(location) }

    fun stop() = call { client.setMockMode(false) }

    private fun call(request: () -> Task<Void>) {
        try {
            request().addOnFailureListener(::logFailure)
        } catch (e: SecurityException) {
            logFailure(e)
        } catch (e: IllegalStateException) {
            logFailure(e)
        }
    }

    private fun logFailure(error: Exception) {
        if (failureLogged) return
        failureLogged = true
        Log.w(TAG, "Play services mock mode unavailable; test providers only", error)
    }

    companion object {
        private const val TAG = "Mockarr"

        /** Null on a device without a usable Play services: the platform test providers carry on alone. */
        fun createOrNull(context: Context): PlayServicesMock? {
            val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            return if (status == ConnectionResult.SUCCESS) {
                PlayServicesMock(LocationServices.getFusedLocationProviderClient(context))
            } else {
                null
            }
        }
    }
}
