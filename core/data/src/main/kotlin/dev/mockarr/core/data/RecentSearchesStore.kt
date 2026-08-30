package dev.mockarr.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.routing.GeocodingResult
import dev.mockarr.core.routing.PlaceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A place the user picked from search before — shown on focus, matched while typing. */
@Serializable
data class RecentSearch(
    val name: String,
    val secondary: String? = null,
    val kind: PlaceKind = PlaceKind.OTHER,
    val latitude: Double,
    val longitude: Double,
    val pickedAtMillis: Long,
) {
    fun toResult(): GeocodingResult =
        GeocodingResult(name = name, position = LatLng(latitude, longitude), secondary = secondary, kind = kind)

    companion object {
        fun from(result: GeocodingResult, nowMillis: Long): RecentSearch = RecentSearch(
            name = result.name,
            secondary = result.secondary,
            kind = result.kind,
            latitude = result.position.latitude,
            longitude = result.position.longitude,
            pickedAtMillis = nowMillis,
        )
    }
}

/**
 * The last [MAX_RECENTS] picked search results, newest first, in their own
 * DataStore file (one JSON string) so settings stay a flat key set.
 */
class RecentSearchesStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val recents: StateFlow<List<RecentSearch>> = dataStore.data
        .map { decode(it[KEY_RECENTS]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Adds (or bumps) [result]; the same place picked twice keeps one row at the top. */
    suspend fun remember(result: GeocodingResult, nowMillis: Long) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_RECENTS])
            val entry = RecentSearch.from(result, nowMillis)
            val kept = current.filterNot { it.samePlace(entry) }
            prefs[KEY_RECENTS] = json.encodeToString(SERIALIZER, (listOf(entry) + kept).take(MAX_RECENTS))
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY_RECENTS) }
    }

    @Suppress("SwallowedException")
    private fun decode(raw: String?): List<RecentSearch> = try {
        raw?.let { json.decodeFromString(SERIALIZER, it) }.orEmpty()
    } catch (e: SerializationException) {
        emptyList()
    }

    private fun RecentSearch.samePlace(other: RecentSearch): Boolean =
        name.equals(other.name, ignoreCase = true) && latitude == other.latitude && longitude == other.longitude

    companion object {
        const val MAX_RECENTS = 10
        private val KEY_RECENTS = stringPreferencesKey("recent_searches")
        private val SERIALIZER = ListSerializer(RecentSearch.serializer())
    }
}
