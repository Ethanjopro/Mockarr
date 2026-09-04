package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.IOException
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Typeahead place search backed by the public Photon (komoot) geocoder —
 * OSM-based, built for autocomplete, and supports biasing results toward a
 * coordinate so nearby places rank first. (Nominatim explicitly disallows
 * autocomplete traffic, which is why it isn't used here.)
 */
class PhotonGeocoder(
    userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : Geocoder {

    private interface PhotonApi {
        @GET
        suspend fun search(@Url url: String): PhotonResponse
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val api: PhotonApi = Retrofit.Builder()
        .baseUrl(FALLBACK_BASE_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(UserAgentInterceptor(userAgent))
                .addInterceptor(MinIntervalInterceptor(MIN_REQUEST_INTERVAL_MILLIS))
                .build(),
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PhotonApi::class.java)

    /**
     * Photon's default zoom 12 suits a city view; 16 pulls street-level hits
     * up — `docs/research/search-rnd.md`.
     */
    override suspend fun search(
        query: String,
        bias: LatLng?,
        zoom: Double?,
        limit: Int,
    ): Result<List<GeocodingResult>> {
        val url = searchUrl(baseUrl, query, bias, zoom, limit)
        return request { api.search(url).features.mapNotNull { it.toResultOrNull() }.dedupe() }
    }

    override suspend fun reverse(position: LatLng): Result<PlaceInfo> {
        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append("/reverse?lat=")
            append(position.latitude)
            append("&lon=")
            append(position.longitude)
        }
        return request {
            val properties = api.search(url).features.firstOrNull()?.properties
            PlaceInfo(
                name = properties?.run { name ?: street },
                city = properties?.city,
                street = properties?.street,
                kind = properties?.kind() ?: PlaceKind.OTHER,
            )
        }
    }

    @Suppress("SwallowedException")
    private inline fun <T> request(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: HttpException) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(e)
    } catch (e: SerializationException) {
        Result.failure(e)
    }

    @Serializable
    private data class PhotonResponse(
        val features: List<PhotonFeature> = emptyList(),
    )

    @Serializable
    private data class PhotonFeature(
        val properties: PhotonProperties = PhotonProperties(),
        val geometry: PhotonGeometry? = null,
    ) {
        fun toResultOrNull(): GeocodingResult? {
            val coordinates = geometry?.coordinates ?: return null
            if (coordinates.size < 2) return null
            return properties.toResult(LatLng(coordinates[1], coordinates[0]))
        }
    }

    @Serializable
    private data class PhotonProperties(
        val name: String? = null,
        val housenumber: String? = null,
        val street: String? = null,
        val city: String? = null,
        val state: String? = null,
        val country: String? = null,
        @SerialName("osm_key") val osmKey: String? = null,
        @SerialName("osm_value") val osmValue: String? = null,
    ) {
        fun toResult(position: LatLng): GeocodingResult? {
            val streetLine = listOfNotNull(housenumber, street).takeIf { it.isNotEmpty() }?.joinToString(" ")
            val primary = name ?: streetLine ?: return null
            return GeocodingResult(
                name = primary,
                position = position,
                secondary = secondaryLine(primary),
                kind = kind(),
                city = city,
            )
        }

        /** "Street, City, State" — whatever locates [primary] without repeating it. */
        private fun secondaryLine(primary: String): String? {
            val parts = listOfNotNull(street, city, state, country)
                .distinct()
                .filterNot { it.equals(primary, ignoreCase = true) }
            return parts.take(MAX_SECONDARY_PARTS).takeIf { it.isNotEmpty() }?.joinToString(", ")
        }

        fun kind(): PlaceKind = when {
            housenumber != null -> PlaceKind.ADDRESS
            osmKey == "highway" -> PlaceKind.STREET
            osmKey == "place" && osmValue in SETTLEMENT_VALUES -> PlaceKind.CITY
            osmKey == "place" || osmKey == "boundary" -> PlaceKind.REGION
            osmKey in POI_KEYS -> PlaceKind.POI
            else -> PlaceKind.OTHER
        }
    }

    @Serializable
    private data class PhotonGeometry(
        val coordinates: List<Double> = emptyList(),
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://photon.komoot.io"
        private const val FALLBACK_BASE_URL = "$DEFAULT_BASE_URL/"
        private const val MIN_REQUEST_INTERVAL_MILLIS = 250L
        private const val MAX_SECONDARY_PARTS = 3

        private val SETTLEMENT_VALUES = setOf(
            "city", "town", "village", "hamlet", "suburb", "neighbourhood", "quarter", "borough", "locality",
        )
        private val POI_KEYS = setOf(
            "amenity", "shop", "tourism", "leisure", "office", "craft", "sport", "historic",
            "aeroway", "railway", "public_transport", "natural", "building", "man_made",
        )

        /** The forward-search URL Photon receives; public so tests pin the parameters. */
        fun searchUrl(baseUrl: String, query: String, bias: LatLng?, zoom: Double?, limit: Int): String =
            buildString {
                append(baseUrl.trimEnd('/'))
                append("/api/?q=")
                append(URLEncoder.encode(query, Charsets.UTF_8.name()))
                append("&limit=")
                append(limit)
                if (bias != null) {
                    append("&lat=")
                    append(bias.latitude)
                    append("&lon=")
                    append(bias.longitude)
                }
                if (zoom != null) {
                    append("&zoom=")
                    append(zoom.roundToInt())
                }
            }
    }
}
