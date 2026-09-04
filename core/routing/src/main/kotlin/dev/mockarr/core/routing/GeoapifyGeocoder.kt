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

/**
 * [Geocoder] backed by Geoapify's Address Autocomplete and Reverse Geocoding
 * APIs (ADR 0003). OSM-based like Photon, with proximity bias; there is no
 * zoom parameter, so [search]'s zoom is accepted and ignored.
 */
class GeoapifyGeocoder(
    private val apiKey: String,
    userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : Geocoder {

    private interface GeoapifyApi {
        @GET
        suspend fun get(@Url url: String): GeoapifyResponse
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val api: GeoapifyApi = Retrofit.Builder()
        .baseUrl(FALLBACK_BASE_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(UserAgentInterceptor(userAgent))
                .addInterceptor(MinIntervalInterceptor(MIN_REQUEST_INTERVAL_MILLIS))
                .build(),
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GeoapifyApi::class.java)

    override suspend fun search(
        query: String,
        bias: LatLng?,
        zoom: Double?,
        limit: Int,
    ): Result<List<GeocodingResult>> = request {
        api.get(searchUrl(baseUrl, apiKey, query, bias, limit)).results.mapNotNull { it.toResultOrNull() }.dedupe()
    }

    override suspend fun reverse(position: LatLng): Result<PlaceInfo> = request {
        val hit = api.get(reverseUrl(baseUrl, apiKey, position)).results.firstOrNull()
        PlaceInfo(
            name = hit?.run { name ?: street },
            city = hit?.city,
            street = hit?.street,
            kind = hit?.kind() ?: PlaceKind.OTHER,
        )
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
    private data class GeoapifyResponse(val results: List<GeoapifyHit> = emptyList())

    @Serializable
    private data class GeoapifyHit(
        val name: String? = null,
        val housenumber: String? = null,
        val street: String? = null,
        val city: String? = null,
        val state: String? = null,
        val country: String? = null,
        @SerialName("address_line1") val addressLine1: String? = null,
        @SerialName("address_line2") val addressLine2: String? = null,
        @SerialName("result_type") val resultType: String? = null,
        val category: String? = null,
        val lat: Double? = null,
        val lon: Double? = null,
    ) {
        fun toResultOrNull(): GeocodingResult? {
            val position = LatLng(lat ?: return null, lon ?: return null)
            val streetLine = listOfNotNull(housenumber, street).takeIf { it.isNotEmpty() }?.joinToString(" ")
            val primary = name ?: streetLine ?: addressLine1 ?: return null
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
                ?: addressLine2?.takeIf { !it.equals(primary, ignoreCase = true) }
        }

        fun kind(): PlaceKind = when {
            housenumber != null && resultType != "amenity" -> PlaceKind.ADDRESS
            resultType == "amenity" || (category != null && name != null) -> PlaceKind.POI
            resultType == "building" -> PlaceKind.ADDRESS
            resultType == "street" -> PlaceKind.STREET
            resultType in SETTLEMENT_TYPES -> PlaceKind.CITY
            resultType in REGION_TYPES -> PlaceKind.REGION
            else -> PlaceKind.OTHER
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.geoapify.com"
        private const val FALLBACK_BASE_URL = "$DEFAULT_BASE_URL/"
        private const val MIN_REQUEST_INTERVAL_MILLIS = 250L
        private const val MAX_SECONDARY_PARTS = 3
        private val SETTLEMENT_TYPES = setOf("city", "district", "locality", "suburb", "neighbourhood", "postcode")
        private val REGION_TYPES = setOf("county", "state", "country")

        /** The autocomplete URL Geoapify receives; public so tests pin the parameters. */
        fun searchUrl(baseUrl: String, apiKey: String, query: String, bias: LatLng?, limit: Int): String =
            buildString {
                append(baseUrl.trimEnd('/'))
                append("/v1/geocode/autocomplete?text=")
                append(URLEncoder.encode(query, Charsets.UTF_8.name()))
                append("&limit=")
                append(limit)
                append("&format=json")
                if (bias != null) {
                    append("&bias=proximity:")
                    append(bias.longitude)
                    append(',')
                    append(bias.latitude)
                }
                append("&apiKey=")
                append(apiKey)
            }

        fun reverseUrl(baseUrl: String, apiKey: String, position: LatLng): String = buildString {
            append(baseUrl.trimEnd('/'))
            append("/v1/geocode/reverse?lat=")
            append(position.latitude)
            append("&lon=")
            append(position.longitude)
            append("&format=json&apiKey=")
            append(apiKey)
        }
    }
}
