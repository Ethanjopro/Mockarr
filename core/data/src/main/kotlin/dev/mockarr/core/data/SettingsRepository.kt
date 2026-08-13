package dev.mockarr.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.mockarr.core.model.RoutingProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MockarrSettings(
    val osrmBaseUrl: String = DEFAULT_OSRM_BASE_URL,
    val tileStyleUrl: String = DEFAULT_TILE_STYLE_URL,
    val tickHz: Double = 1.0,
    val jitterEnabled: Boolean = true,
    val jitterSigmaMeters: Double = 3.0,
    val defaultProfile: RoutingProfile = RoutingProfile.DRIVING,
) {
    /** Walking/cycling need a full OSRM install; the public demo only serves driving. */
    val customServerConfigured: Boolean
        get() = osrmBaseUrl.trimEnd('/') != DEFAULT_OSRM_BASE_URL

    companion object {
        const val DEFAULT_OSRM_BASE_URL = "https://router.project-osrm.org"
        const val DEFAULT_TILE_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    }
}

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {

    val settings: StateFlow<MockarrSettings> = dataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, MockarrSettings())

    suspend fun setOsrmBaseUrl(url: String) = edit { prefs ->
        prefs[KEY_OSRM_URL] = url.trim().trimEnd('/').ifEmpty { MockarrSettings.DEFAULT_OSRM_BASE_URL }
    }

    suspend fun setTileStyleUrl(url: String) = edit { prefs ->
        prefs[KEY_TILE_URL] = url.trim().ifEmpty { MockarrSettings.DEFAULT_TILE_STYLE_URL }
    }

    suspend fun setTickHz(value: Double) = edit { it[KEY_TICK_HZ] = value.coerceIn(0.5, 5.0) }

    suspend fun setJitterEnabled(value: Boolean) = edit { it[KEY_JITTER_ENABLED] = value }

    suspend fun setJitterSigmaMeters(value: Double) = edit { it[KEY_JITTER_SIGMA] = value.coerceIn(0.5, 10.0) }

    suspend fun setDefaultProfile(profile: RoutingProfile) = edit { it[KEY_PROFILE] = profile.name }

    private suspend fun edit(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { transform(it) }
    }

    private fun Preferences.toSettings(): MockarrSettings = MockarrSettings(
        osrmBaseUrl = this[KEY_OSRM_URL] ?: MockarrSettings.DEFAULT_OSRM_BASE_URL,
        tileStyleUrl = this[KEY_TILE_URL] ?: MockarrSettings.DEFAULT_TILE_STYLE_URL,
        tickHz = this[KEY_TICK_HZ] ?: 1.0,
        jitterEnabled = this[KEY_JITTER_ENABLED] ?: true,
        jitterSigmaMeters = this[KEY_JITTER_SIGMA] ?: 3.0,
        defaultProfile = this[KEY_PROFILE]
            ?.let { runCatching { RoutingProfile.valueOf(it) }.getOrNull() }
            ?: RoutingProfile.DRIVING,
    )

    private companion object {
        val KEY_OSRM_URL = stringPreferencesKey("osrm_base_url")
        val KEY_TILE_URL = stringPreferencesKey("tile_style_url")
        val KEY_TICK_HZ = doublePreferencesKey("tick_hz")
        val KEY_JITTER_ENABLED = booleanPreferencesKey("jitter_enabled")
        val KEY_JITTER_SIGMA = doublePreferencesKey("jitter_sigma_m")
        val KEY_PROFILE = stringPreferencesKey("default_profile")
    }
}
