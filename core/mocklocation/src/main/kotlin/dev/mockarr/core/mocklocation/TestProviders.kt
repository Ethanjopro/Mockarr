package dev.mockarr.core.mocklocation

import android.location.LocationManager
import android.location.provider.ProviderProperties

/**
 * Registers a test provider. Positional args (Java API): requiresNetwork,
 * requiresSatellite, requiresCell, hasMonetaryCost, supportsAltitude,
 * supportsSpeed, supportsBearing. ProviderProperties constants are
 * compile-time-inlined ints, safe below API 31.
 */
internal fun LocationManager.registerTestProvider(
    provider: String,
    requiresSatellite: Boolean = false,
    supportsMotion: Boolean = false,
) {
    addTestProvider(
        provider,
        false,
        requiresSatellite,
        false,
        false,
        supportsMotion,
        supportsMotion,
        supportsMotion,
        ProviderProperties.POWER_USAGE_LOW,
        ProviderProperties.ACCURACY_FINE,
    )
}
