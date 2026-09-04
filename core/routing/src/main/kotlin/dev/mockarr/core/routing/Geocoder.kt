package dev.mockarr.core.routing

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng

/** What kind of place a search hit is — drives the row glyph. */
enum class PlaceKind { POI, STREET, ADDRESS, CITY, REGION, OTHER }

/** A place found by free-text search: a primary name and a "where" line. */
data class GeocodingResult(
    val name: String,
    val position: LatLng,
    val secondary: String? = null,
    val kind: PlaceKind = PlaceKind.OTHER,
    val city: String? = null,
)

/** What a coordinate reverse-geocodes to: a local name (POI/street), the street itself, and its city. */
data class PlaceInfo(
    val name: String?,
    val city: String?,
    val street: String? = null,
    val kind: PlaceKind = PlaceKind.OTHER,
) {
    /**
     * The name a person would give the spot: a POI's street rather than the
     * shop itself ("Budget to Rue La Fayette" read as a bug in a route title).
     */
    fun routeEndpointName(): String? = if (kind == PlaceKind.POI) street ?: name else name
}

/**
 * Place search and reverse lookup, provider-neutral. The app depends on this,
 * never on a concrete client, so the search backend can change with one new
 * implementation and one DI binding (ADR 0002 — backend flexibility).
 */
interface Geocoder {
    /**
     * [bias] is the point results rank around; [zoom] is the camera zoom, which
     * sets how tightly they cluster (a city view vs. street-level hits).
     */
    suspend fun search(
        query: String,
        bias: LatLng? = null,
        zoom: Double? = null,
        limit: Int = DEFAULT_LIMIT,
    ): Result<List<GeocodingResult>>

    /** Local name + city for a coordinate ("what street/place is this?"). */
    suspend fun reverse(position: LatLng): Result<PlaceInfo>

    companion object {
        const val DEFAULT_LIMIT = 8
    }
}

/** Two hits closer than this with the same name are one place (subway entrances, POI + node). */
private const val DUPLICATE_METERS = 50.0

/**
 * Geocoders return distinct OSM objects for one place — every subway entrance,
 * a POI and its building, a bridge's way and relation. Same name + city, same
 * name + "where" line, or same name within [DUPLICATE_METERS], collapses to the
 * first (best-ranked) hit.
 */
fun List<GeocodingResult>.dedupe(): List<GeocodingResult> {
    val kept = mutableListOf<GeocodingResult>()
    for (candidate in this) {
        val duplicate = kept.any { existing ->
            existing.name.equals(candidate.name, ignoreCase = true) &&
                (existing.sameCity(candidate) || existing.sameSecondary(candidate) || existing.near(candidate))
        }
        if (!duplicate) kept += candidate
    }
    return kept
}

private fun GeocodingResult.sameCity(other: GeocodingResult): Boolean =
    city != null && city.equals(other.city, ignoreCase = true)

private fun GeocodingResult.sameSecondary(other: GeocodingResult): Boolean =
    secondary != null && secondary.equals(other.secondary, ignoreCase = true)

private fun GeocodingResult.near(other: GeocodingResult): Boolean =
    GeoMath.distanceMeters(position, other.position) < DUPLICATE_METERS
