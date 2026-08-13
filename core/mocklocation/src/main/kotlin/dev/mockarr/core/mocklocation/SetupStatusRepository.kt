package dev.mockarr.core.mocklocation

import android.content.Context
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.provider.Settings

data class SetupStatus(
    val developerOptionsEnabled: Boolean,
    val selectedAsMockLocationApp: Boolean,
) {
    val readyToMock: Boolean get() = developerOptionsEnabled && selectedAsMockLocationApp
}

/**
 * Detects whether the device is set up for mocking. Re-check on every screen
 * resume — the user toggles these in system Settings, outside our process.
 */
class SetupStatusRepository(
    private val context: Context,
    private val locationManager: LocationManager,
    private val controller: MockLocationController,
) {

    fun check(): SetupStatus = SetupStatus(
        developerOptionsEnabled = isDeveloperOptionsEnabled(),
        selectedAsMockLocationApp = isSelectedAsMockApp(),
    )

    private fun isDeveloperOptionsEnabled(): Boolean = Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
        0,
    ) == 1

    /**
     * OEM-proof detection: registering a test provider throws SecurityException
     * exactly when we are not the selected mock location app. Skipped while a
     * mock session is live, so the probe never disturbs active providers.
     */
    private fun isSelectedAsMockApp(): Boolean {
        if (controller.isRunning) return true
        return try {
            probeAddRemove()
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            // The provider registry rejected the probe for a non-permission reason
            // (OEM quirk); permission-wise we got past the security check.
            true
        }
    }

    // Positional args (Java API): requiresNetwork, requiresSatellite, requiresCell,
    // hasMonetaryCost, supportsAltitude, supportsSpeed, supportsBearing.
    private fun probeAddRemove() {
        locationManager.addTestProvider(
            LocationManager.NETWORK_PROVIDER,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            ProviderProperties.POWER_USAGE_LOW,
            ProviderProperties.ACCURACY_FINE,
        )
        locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
    }
}
