package dev.mockarr.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.MapCamera
import dev.mockarr.core.model.RoutingProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

data class MockarrSettings(
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
    /** Routes may leave the road network to reach a stop the router can't (dotted connectors). */
    val offRoadEnabled: Boolean = true,

    /** Pause at the road's edge, then cross off-road connectors at walking pace. */
    val offRoadWalkEnabled: Boolean = true,
    val setupSeen: Boolean = false,
    /** The one-time "pull the sheet up" hint on the Map has been dismissed. */
    val sheetHintSeen: Boolean = false,
) {
    companion object {
        const val DEFAULT_TILE_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

        // Dark-recolored liberty bundled as an app asset (scripts/make_dark_style.py);
        // OpenFreeMap's hosted dark style lacks 3D buildings, POIs, and most labels.
        const val DEFAULT_TILE_STYLE_URL_DARK = "asset://liberty_dark.json"
        const val TICK_HZ_MIN = 0.5
        const val TICK_HZ_MAX = 5.0
        const val JITTER_SIGMA_MIN = 0.0
        const val JITTER_SIGMA_MAX = 10.0
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

    suspend fun setOffRoadEnabled(value: Boolean) = edit { it[KEY_OFF_ROAD] = value }

    suspend fun setOffRoadWalkEnabled(value: Boolean) = edit { it[KEY_OFF_ROAD_WALK] = value }

    suspend fun setSetupSeen(value: Boolean) = edit { it[KEY_SETUP_SEEN] = value }

    suspend fun setSheetHintSeen(value: Boolean) = edit { it[KEY_SHEET_HINT_SEEN] = value }

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
            tileStyleUrl = this[KEY_TILE_URL] ?: DEFAULTS.tileStyleUrl,
            tickHz = this[KEY_TICK_HZ] ?: DEFAULTS.tickHz,
            jitterSigmaMeters = this[KEY_JITTER_SIGMA] ?: DEFAULTS.jitterSigmaMeters,
            defaultProfile = this[KEY_PROFILE]
                ?.let { RoutingProfile.fromNameOrDefault(it) }
                ?: DEFAULTS.defaultProfile,
            units = DistanceUnits.fromNameOrNull(this[KEY_UNITS]) ?: DEFAULTS.units,
            lastCamera = toCamera(),
        ),
    )

    private fun Preferences.withFlags(base: MockarrSettings): MockarrSettings = base.copy(
        jitterEnabled = this[KEY_JITTER_ENABLED] ?: DEFAULTS.jitterEnabled,
        stayAtDestination = this[KEY_STAY_AT_DESTINATION] ?: DEFAULTS.stayAtDestination,
        map3dEnabled = this[KEY_MAP_3D] ?: DEFAULTS.map3dEnabled,
        trafficSimEnabled = this[KEY_TRAFFIC_SIM] ?: DEFAULTS.trafficSimEnabled,
        offRoadEnabled = this[KEY_OFF_ROAD] ?: DEFAULTS.offRoadEnabled,
        offRoadWalkEnabled = this[KEY_OFF_ROAD_WALK] ?: DEFAULTS.offRoadWalkEnabled,
        setupSeen = this[KEY_SETUP_SEEN] ?: DEFAULTS.setupSeen,
        sheetHintSeen = this[KEY_SHEET_HINT_SEEN] ?: DEFAULTS.sheetHintSeen,
    )

    private fun Preferences.toCamera(): MapCamera? {
        val lat = this[KEY_CAMERA_LAT] ?: return null
        val lng = this[KEY_CAMERA_LNG] ?: return null
        val zoom = this[KEY_CAMERA_ZOOM] ?: return null
        return MapCamera(LatLng(lat, lng), zoom)
    }

    private companion object {
        val DEFAULTS = MockarrSettings()
        val KEY_TILE_URL = stringPreferencesKey("tile_style_url")
        val KEY_TICK_HZ = doublePreferencesKey("tick_hz")
        val KEY_JITTER_ENABLED = booleanPreferencesKey("jitter_enabled")
        val KEY_JITTER_SIGMA = doublePreferencesKey("jitter_sigma_m")
        val KEY_PROFILE = stringPreferencesKey("default_profile")
        val KEY_UNITS = stringPreferencesKey("distance_units")
        val KEY_STAY_AT_DESTINATION = booleanPreferencesKey("stay_at_destination")
        val KEY_MAP_3D = booleanPreferencesKey("map_3d_enabled")
        val KEY_TRAFFIC_SIM = booleanPreferencesKey("traffic_sim_enabled")
        val KEY_OFF_ROAD = booleanPreferencesKey("off_road_enabled")
        val KEY_OFF_ROAD_WALK = booleanPreferencesKey("off_road_walk_enabled")
        val KEY_SETUP_SEEN = booleanPreferencesKey("setup_seen")
        val KEY_SHEET_HINT_SEEN = booleanPreferencesKey("sheet_hint_seen")
        val KEY_CAMERA_LAT = doublePreferencesKey("camera_lat")
        val KEY_CAMERA_LNG = doublePreferencesKey("camera_lng")
        val KEY_CAMERA_ZOOM = doublePreferencesKey("camera_zoom")
    }
}
