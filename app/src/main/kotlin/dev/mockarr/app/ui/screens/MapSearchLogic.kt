package dev.mockarr.app.ui.screens

import dev.mockarr.core.data.RecentSearch
import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.routing.GeocodingResult
import dev.mockarr.core.routing.PlaceKind

/**
 * The pure half of place search (`docs/research/search-rnd.md` §4): which
 * point results rank around, what to show before the network answers, and how
 * recents merge with hits. No Android types, so it unit-tests on the JVM.
 */

/** One row in the results list. [recent] rows wear the history glyph. */
data class SearchSuggestion(
    val result: GeocodingResult,
    val distanceMeters: Double?,
    val recent: Boolean = false,
)

/**
 * Where results rank around. For a mock-location app the map viewport is the
 * intent, so the camera outranks the phone's real fix — a stale real fix used
 * to pull "Starbucks, Ukiah" over the Mountain View one on screen.
 */
fun searchAnchor(mocked: LatLng?, camera: LatLng?, real: LatLng?): LatLng? = mocked ?: camera ?: real

/**
 * The zoom Photon biases around. Measured 2026-08-30 (`search-rnd.md` §1):
 * at 14+ the bias radius is so tight that a street with no local match
 * ("25th ave" from Mountain View) loses to Phoenix and Denver; 12 keeps POIs
 * in the city and streets in the county, and a zoomed-out map widens it.
 */
fun biasZoom(cameraZoom: Double?): Double? = cameraZoom?.coerceIn(MIN_BIAS_ZOOM, MAX_BIAS_ZOOM)

private const val MIN_BIAS_ZOOM = 8.0
private const val MAX_BIAS_ZOOM = 12.0

/**
 * How close a search pick lands: a building at street level, a street with
 * its block, a town as a whole. Photon's `extent` is not parsed, so the place
 * kind stands in for its size.
 */
fun searchZoomFor(kind: PlaceKind): Double = when (kind) {
    PlaceKind.REGION -> REGION_ZOOM
    PlaceKind.CITY -> CITY_ZOOM
    PlaceKind.STREET -> STREET_ZOOM
    PlaceKind.POI, PlaceKind.ADDRESS, PlaceKind.OTHER -> PLACE_ZOOM
}

private const val PLACE_ZOOM = 17.0
private const val STREET_ZOOM = 16.0
private const val CITY_ZOOM = 13.0
private const val REGION_ZOOM = 10.0

/**
 * Results for shorter spellings of the current query, filtered client-side:
 * while "starb" is in flight, the cached "star" hits that still contain
 * "starb" render at once. Bounded LRU keyed by the trimmed lower-case query.
 */
class PrefixCache(private val capacity: Int = DEFAULT_CAPACITY) {
    private val entries = object : LinkedHashMap<String, List<GeocodingResult>>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<GeocodingResult>>?): Boolean =
            size > capacity
    }

    fun put(query: String, results: List<GeocodingResult>) {
        entries[query.normalized()] = results
    }

    /** The exact hit, else the longest cached prefix narrowed to names still matching [query]. */
    fun lookup(query: String): List<GeocodingResult>? {
        val key = query.normalized()
        val prefix = entries.keys.filter { key.startsWith(it) }.maxByOrNull { it.length } ?: return null
        val hits = entries.getValue(prefix)
        return if (prefix == key) hits else hits.filter { it.name.contains(key, ignoreCase = true) }
    }

    private fun String.normalized() = trim().lowercase()

    private companion object {
        const val DEFAULT_CAPACITY = 32
        const val LOAD_FACTOR = 0.75f
    }
}

/** Recents whose name starts with [query] (all of them for an empty query), newest first. */
fun matchingRecents(recents: List<RecentSearch>, query: String): List<RecentSearch> {
    val key = query.trim()
    return recents.filter { key.isEmpty() || it.name.startsWith(key, ignoreCase = true) }
}

/**
 * Recents first (they are instant and were wanted before), then network hits
 * that are not one of those recents — same name within [SAME_PLACE_METERS].
 */
fun mergeSuggestions(
    recents: List<RecentSearch>,
    results: List<GeocodingResult>,
    anchor: LatLng?,
): List<SearchSuggestion> {
    val recentResults = recents.map { it.toResult() }
    val fresh = results.filterNot { hit ->
        recentResults.any {
            it.name.equals(hit.name, ignoreCase = true) &&
                GeoMath.distanceMeters(it.position, hit.position) < SAME_PLACE_METERS
        }
    }
    val recentRows = recentResults.map { it.suggestion(anchor, recent = true) }
    return recentRows + fresh.map { it.suggestion(anchor, recent = false) }
}

private fun GeocodingResult.suggestion(anchor: LatLng?, recent: Boolean) = SearchSuggestion(
    result = this,
    distanceMeters = anchor?.let { GeoMath.distanceMeters(it, position) },
    recent = recent,
)

private const val SAME_PLACE_METERS = 50.0
