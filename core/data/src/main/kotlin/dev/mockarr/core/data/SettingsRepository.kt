package dev.mockarr.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.MapCamera
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.routing.OsrmRouteProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

data class MockarrSettings(
    val osrmBaseUrl: String = DEFAULT_OSRM_BASE_URL,
    val tileStyleUrl: String = DEFAULT_TILE_STYLE_URL,
    val tickHz: Double = 1.0,
    val jitterEnabled: Boolean = true,
    val jitterSigmaMeters: Double = 3.0,
    val defaultProfile: RoutingProfile = RoutingProfile.DRIVING,
    val units: DistanceUnits = DistanceUnits.defaultForCountry(Locale.getDefault().country),
    val lastCamera: MapCamera? = null,
    val stayAtDestination: Boolean = true,
    val map3dEnabled: Boolean = true,
    val trafficSimEnabled: Boolean = true,
    val setupSeen: Boolean = false,
    val progressAlertEnabled: Boolean = false,
    val progressAlertPercent: Int = 80,
) {
    /** Walking/cycling need a full OSRM install; the public demo only serves driving. */
    val customServerConfigured: Boolean
        get() = osrmBaseUrl != DEFAULT_OSRM_BASE_URL

    companion object {
        const val DEFAULT_OSRM_BASE_URL = OsrmRouteProvider.DEFAULT_BASE_URL
        const val DEFAULT_TILE_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val DEFAULT_TILE_STYLE_URL_DARK = "https://tiles.openfreemap.org/styles/dark"
        const val TICK_HZ_MIN = 0.5
        const val TICK_HZ_MAX = 5.0
        const val JITTER_SIGMA_MIN = 0.0
        const val JITTER_SIGMA_MAX = 10.0
        const val PROGRESS_ALERT_MIN = 10
        const val PROGRESS_ALERT_MAX = 95

        /** Canonical form for user-entered base URLs. */
        fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')
    }
}

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {

    val settings: StateFlow<MockarrSettings> = dataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, MockarrSettings())

    /**
     * Settings straight from disk — unlike [settings], this can't return the
     * in-memory defaults before DataStore has loaded (the cold-start race).
     */
    suspend fun awaitLoaded(): MockarrSettings = dataStore.data.first().toSettings()

    suspend fun setOsrmBaseUrl(url: String) = edit { prefs ->
        prefs[KEY_OSRM_URL] = MockarrSettings.normalizeBaseUrl(url)
            .ifEmpty { MockarrSettings.DEFAULT_OSRM_BASE_URL }
    }

    suspend fun setTileStyleUrl(url: String) = edit { prefs ->
        prefs[KEY_TILE_URL] = url.trim().ifEmpty { MockarrSettings.DEFAULT_TILE_STYLE_URL }
    }

    suspend fun setTickHz(value: Double) = edit {
        it[KEY_TICK_HZ] = value.coerceIn(MockarrSettings.TICK_HZ_MIN, MockarrSettings.TICK_HZ_MAX)
    }

    suspend fun setJitterEnabled(value: Boolean) = edit { it[KEY_JITTER_ENABLED] = value }

    suspend fun setJitterSigmaMeters(value: Double) = edit {
        it[KEY_JITTER_SIGMA] = value.coerceIn(
            MockarrSettings.JITTER_SIGMA_MIN,
            MockarrSettings.JITTER_SIGMA_MAX,
        )
    }

    suspend fun setDefaultProfile(profile: RoutingProfile) = edit { it[KEY_PROFILE] = profile.name }

    suspend fun setUnits(units: DistanceUnits) = edit { it[KEY_UNITS] = units.name }

    suspend fun setStayAtDestination(value: Boolean) = edit { it[KEY_STAY_AT_DESTINATION] = value }

    suspend fun setMap3dEnabled(value: Boolean) = edit { it[KEY_MAP_3D] = value }

    suspend fun setTrafficSimEnabled(value: Boolean) = edit { it[KEY_TRAFFIC_SIM] = value }

    suspend fun setSetupSeen(value: Boolean) = edit { it[KEY_SETUP_SEEN] = value }

    suspend fun setProgressAlertEnabled(value: Boolean) = edit { it[KEY_PROGRESS_ALERT] = value }

    suspend fun setProgressAlertPercent(value: Int) = edit {
        it[KEY_PROGRESS_ALERT_PERCENT] = value.coerceIn(
            MockarrSettings.PROGRESS_ALERT_MIN,
            MockarrSettings.PROGRESS_ALERT_MAX,
        )
    }

    suspend fun setLastCamera(camera: MapCamera) = edit {
        it[KEY_CAMERA_LAT] = camera.target.latitude
        it[KEY_CAMERA_LNG] = camera.target.longitude
        it[KEY_CAMERA_ZOOM] = camera.zoom
    }

    private suspend fun edit(transform: (MutablePreferences) -> Unit) {
        dataStore.edit { transform(it) }
    }

    private fun Preferences.toSettings(): MockarrSettings = withFlags(
        MockarrSettings(
            osrmBaseUrl = this[KEY_OSRM_URL] ?: DEFAULTS.osrmBaseUrl,
            tileStyleUrl = this[KEY_TILE_URL] ?: DEFAULTS.tileStyleUrl,
            tickHz = this[KEY_TICK_HZ] ?: DEFAULTS.tickHz,
            jitterSigmaMeters = this[KEY_JITTER_SIGMA] ?: DEFAULTS.jitterSigmaMeters,
            defaultProfile = this[KEY_PROFILE]
                ?.let { RoutingProfile.fromNameOrDefault(it) }
                ?: DEFAULTS.defaultProfile,
            units = DistanceUnits.fromNameOrNull(this[KEY_UNITS]) ?: DEFAULTS.units,
            lastCamera = toCamera(),
            progressAlertPercent = this[KEY_PROGRESS_ALERT_PERCENT] ?: DEFAULTS.progressAlertPercent,
        ),
    )

    private fun Preferences.withFlags(base: MockarrSettings): MockarrSettings = base.copy(
        jitterEnabled = this[KEY_JITTER_ENABLED] ?: DEFAULTS.jitterEnabled,
        stayAtDestination = this[KEY_STAY_AT_DESTINATION] ?: DEFAULTS.stayAtDestination,
        map3dEnabled = this[KEY_MAP_3D] ?: DEFAULTS.map3dEnabled,
        trafficSimEnabled = this[KEY_TRAFFIC_SIM] ?: DEFAULTS.trafficSimEnabled,
        setupSeen = this[KEY_SETUP_SEEN] ?: DEFAULTS.setupSeen,
        progressAlertEnabled = this[KEY_PROGRESS_ALERT] ?: DEFAULTS.progressAlertEnabled,
    )

    private fun Preferences.toCamera(): MapCamera? {
        val lat = this[KEY_CAMERA_LAT] ?: return null
        val lng = this[KEY_CAMERA_LNG] ?: return null
        val zoom = this[KEY_CAMERA_ZOOM] ?: return null
        return MapCamera(LatLng(lat, lng), zoom)
    }

    private companion object {
        val DEFAULTS = MockarrSettings()
        val KEY_OSRM_URL = stringPreferencesKey("osrm_base_url")
        val KEY_TILE_URL = stringPreferencesKey("tile_style_url")
        val KEY_TICK_HZ = doublePreferencesKey("tick_hz")
        val KEY_JITTER_ENABLED = booleanPreferencesKey("jitter_enabled")
        val KEY_JITTER_SIGMA = doublePreferencesKey("jitter_sigma_m")
        val KEY_PROFILE = stringPreferencesKey("default_profile")
        val KEY_UNITS = stringPreferencesKey("distance_units")
        val KEY_STAY_AT_DESTINATION = booleanPreferencesKey("stay_at_destination")
        val KEY_MAP_3D = booleanPreferencesKey("map_3d_enabled")
        val KEY_TRAFFIC_SIM = booleanPreferencesKey("traffic_sim_enabled")
        val KEY_SETUP_SEEN = booleanPreferencesKey("setup_seen")
        val KEY_PROGRESS_ALERT = booleanPreferencesKey("progress_alert_enabled")
        val KEY_PROGRESS_ALERT_PERCENT = intPreferencesKey("progress_alert_percent")
        val KEY_CAMERA_LAT = doublePreferencesKey("camera_lat")
        val KEY_CAMERA_LNG = doublePreferencesKey("camera_lng")
        val KEY_CAMERA_ZOOM = doublePreferencesKey("camera_zoom")
    }
}
