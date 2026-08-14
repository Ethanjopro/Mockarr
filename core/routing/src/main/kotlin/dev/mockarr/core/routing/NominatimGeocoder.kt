package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import kotlinx.serialization.SerialName
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
 * Free-text place search backed by the public Nominatim (OpenStreetMap)
 * geocoder. Usage policy requires an identifying User-Agent and at most one
 * request per second — both enforced by the client here.
 */
class NominatimGeocoder(
    userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    private interface NominatimApi {
        @GET
        suspend fun search(@Url url: String): List<NominatimPlace>
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val api: NominatimApi = Retrofit.Builder()
        .baseUrl(FALLBACK_BASE_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(UserAgentInterceptor(userAgent))
                .addInterceptor(MinIntervalInterceptor(MIN_REQUEST_INTERVAL_MILLIS))
                .build(),
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(NominatimApi::class.java)

    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): Result<List<GeocodingResult>> {
        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append("/search?format=jsonv2&limit=")
            append(limit)
            append("&q=")
            append(URLEncoder.encode(query, Charsets.UTF_8.name()))
        }
        return try {
            Result.success(api.search(url).mapNotNull { it.toResultOrNull() })
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    @Serializable
    private data class NominatimPlace(
        @SerialName("display_name") val displayName: String = "",
        val lat: String = "",
        val lon: String = "",
    ) {
        fun toResultOrNull(): GeocodingResult? {
            val latitude = lat.toDoubleOrNull() ?: return null
            val longitude = lon.toDoubleOrNull() ?: return null
            return GeocodingResult(displayName, LatLng(latitude, longitude))
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://nominatim.openstreetmap.org"
        private const val FALLBACK_BASE_URL = "$DEFAULT_BASE_URL/"
        private const val MIN_REQUEST_INTERVAL_MILLIS = 1_100L
        private const val DEFAULT_LIMIT = 5
    }
}
