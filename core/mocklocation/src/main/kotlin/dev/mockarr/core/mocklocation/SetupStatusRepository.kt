package dev.mockarr.core.mocklocation

import android.content.Context
import android.location.LocationManager
import android.provider.Settings

data class SetupStatus(
    val developerOptionsEnabled: Boolean,
    val selectedAsMockLocationApp: Boolean,
    val notificationsEnabled: Boolean,
    val batteryOptimizationExempt: Boolean,
) {
    /** The two hard requirements; notifications/battery are quality-of-life. */
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
        notificationsEnabled = context.getSystemService(android.app.NotificationManager::class.java)
            ?.areNotificationsEnabled() == true,
        batteryOptimizationExempt = context.getSystemService(android.os.PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true,
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

    private fun probeAddRemove() {
        locationManager.registerTestProvider(LocationManager.NETWORK_PROVIDER)
        locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
    }
}
