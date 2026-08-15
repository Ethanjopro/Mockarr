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
import java.util.Locale

/**
 * Terrain elevation lookups via the free Open-Meteo elevation API
 * (Copernicus DEM). Batches up to [MAX_COORDS_PER_REQUEST] coordinates per
 * request; no API key required.
 */
class OpenMeteoElevationClient(
    userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    private interface ElevationApi {
        @GET
        suspend fun elevations(@Url url: String): ElevationResponse
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val api: ElevationApi = Retrofit.Builder()
        .baseUrl(FALLBACK_BASE_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(UserAgentInterceptor(userAgent))
                .addInterceptor(MinIntervalInterceptor(MIN_REQUEST_INTERVAL_MILLIS))
                .build(),
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ElevationApi::class.java)

    /** Elevation in meters for each input coordinate, in order. */
    suspend fun elevations(coordinates: List<LatLng>): Result<List<Double>> {
        if (coordinates.isEmpty()) return Result.success(emptyList())
        return try {
            val result = coordinates.chunked(MAX_COORDS_PER_REQUEST).flatMap { chunk ->
                val response = api.elevations(chunkUrl(chunk))
                require(response.elevation.size == chunk.size) {
                    "Elevation count ${response.elevation.size} != requested ${chunk.size}"
                }
                response.elevation
            }
            Result.success(result)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    private fun chunkUrl(chunk: List<LatLng>): String = buildString {
        append(baseUrl.trimEnd('/'))
        append("/v1/elevation?latitude=")
        chunk.joinTo(this, ",") { COORD_FORMAT.format(Locale.ROOT, it.latitude) }
        append("&longitude=")
        chunk.joinTo(this, ",") { COORD_FORMAT.format(Locale.ROOT, it.longitude) }
    }

    @Serializable
    private data class ElevationResponse(
        val elevation: List<Double> = emptyList(),
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com"
        const val MAX_COORDS_PER_REQUEST = 100
        private const val FALLBACK_BASE_URL = "$DEFAULT_BASE_URL/"
        private const val MIN_REQUEST_INTERVAL_MILLIS = 500L

        // ~1 m precision — plenty against a 30-90 m resolution terrain model.
        private const val COORD_FORMAT = "%.5f"
    }
}
