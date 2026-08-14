package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import kotlinx.serialization.Serializable
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

/** A place found by free-text search. */
data class GeocodingResult(
    val name: String,
    val position: LatLng,
)

/**
 * Typeahead place search backed by the public Photon (komoot) geocoder —
 * OSM-based, built for autocomplete, and supports biasing results toward a
 * coordinate so nearby places rank first. (Nominatim explicitly disallows
 * autocomplete traffic, which is why it isn't used here.)
 */
class PhotonGeocoder(
    userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

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

    suspend fun search(
        query: String,
        bias: LatLng? = null,
        limit: Int = DEFAULT_LIMIT,
    ): Result<List<GeocodingResult>> {
        val url = buildString {
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
        }
        return try {
            // Photon returns distinct OSM objects that often share a display
            // name (e.g. every subway entrance) — collapse them.
            Result.success(
                api.search(url).features.mapNotNull { it.toResultOrNull() }.distinctBy { it.name },
            )
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
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
            val name = properties.displayName() ?: return null
            return GeocodingResult(name, LatLng(coordinates[1], coordinates[0]))
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
    ) {
        /** "Name, City, State, Country" from Photon's structured fields. */
        fun displayName(): String? {
            val streetLine = listOfNotNull(housenumber, street)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" ")
            val parts = listOfNotNull(name ?: streetLine, city, state, country).distinct()
            return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
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
        private const val DEFAULT_LIMIT = 6
    }
}
