package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.IOException

/**
 * [RouteProvider] backed by an OSRM server (`/route/v1`). Defaults to the public
 * demo server, which reliably serves the driving profile only — a custom server
 * URL (Settings) unlocks walking/cycling.
 */
class OsrmRouteProvider(
    private val baseUrlProvider: () -> String,
    userAgent: String,
) : RouteProvider {

    private interface OsrmApi {
        @GET
        suspend fun route(@Url url: String): OsrmRouteResponse
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val api: OsrmApi = Retrofit.Builder()
        .baseUrl(FALLBACK_BASE_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(UserAgentInterceptor(userAgent))
                .addInterceptor(MinIntervalInterceptor(MIN_REQUEST_INTERVAL_MILLIS))
                .build(),
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OsrmApi::class.java)

    override suspend fun route(waypoints: List<LatLng>, profile: RoutingProfile): Result<Route> {
        require(waypoints.size >= 2) { "At least two waypoints are required" }
        val coords = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = buildString {
            append(baseUrlProvider().trimEnd('/'))
            append("/route/v1/")
            append(profile.osrmName)
            append('/')
            append(coords)
            append("?overview=full&geometries=polyline6&annotations=distance,duration&steps=false")
        }
        return try {
            toResult(api.route(url))
        } catch (e: HttpException) {
            Result.failure(e.toRoutingException())
        } catch (e: IOException) {
            Result.failure(RoutingException.Network(e))
        }
    }

    private fun toResult(response: OsrmRouteResponse): Result<Route> = when {
        response.code == CODE_OK && response.routes.isNotEmpty() ->
            Result.success(response.routes.first().toRoute())
        response.code == CODE_NO_ROUTE || response.routes.isEmpty() ->
            Result.failure(RoutingException.NoRoute())
        else -> Result.failure(RoutingException.Server(response.message ?: response.code))
    }

    private fun HttpException.toRoutingException(): RoutingException {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val bodyCode = body?.let {
            runCatching { json.decodeFromString<OsrmRouteResponse>(it).code }.getOrNull()
        }
        return when {
            bodyCode == CODE_NO_ROUTE -> RoutingException.NoRoute()
            code() == HTTP_TOO_MANY_REQUESTS -> RoutingException.RateLimited()
            else -> RoutingException.Server("HTTP ${code()}")
        }
    }

    private val RoutingProfile.osrmName: String
        get() = when (this) {
            RoutingProfile.DRIVING -> "driving"
            RoutingProfile.WALKING -> "foot"
            RoutingProfile.CYCLING -> "bike"
        }

    @Serializable
    private data class OsrmRouteResponse(
        val code: String,
        val routes: List<OsrmRoute> = emptyList(),
        val message: String? = null,
    )

    @Serializable
    private data class OsrmRoute(
        val distance: Double,
        val duration: Double,
        val geometry: String,
        val legs: List<OsrmLeg> = emptyList(),
    ) {
        fun toRoute(): Route = Route(
            points = Polyline6.decode(geometry),
            legs = legs.map { leg ->
                RouteLeg(
                    segmentDistancesMeters = leg.annotation?.distance.orEmpty(),
                    segmentDurationsSeconds = leg.annotation?.duration.orEmpty(),
                )
            },
            distanceMeters = distance,
            durationSeconds = duration,
        )
    }

    @Serializable
    private data class OsrmLeg(
        @SerialName("annotation") val annotation: OsrmAnnotation? = null,
    )

    @Serializable
    private data class OsrmAnnotation(
        val distance: List<Double> = emptyList(),
        val duration: List<Double> = emptyList(),
    )

    private class UserAgentInterceptor(private val userAgent: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
            chain.request().newBuilder().header("User-Agent", userAgent).build(),
        )
    }

    /** Demo-server etiquette: never fire requests closer together than the interval. */
    private class MinIntervalInterceptor(private val minIntervalMillis: Long) : Interceptor {
        private val lock = Any()
        private var lastRequestAt = 0L

        override fun intercept(chain: Interceptor.Chain): Response {
            synchronized(lock) {
                val wait = lastRequestAt + minIntervalMillis - System.currentTimeMillis()
                if (wait > 0) {
                    Thread.sleep(wait)
                }
                lastRequestAt = System.currentTimeMillis()
            }
            return chain.proceed(chain.request())
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://router.project-osrm.org"
        private const val FALLBACK_BASE_URL = "$DEFAULT_BASE_URL/"
        private const val MIN_REQUEST_INTERVAL_MILLIS = 1_000L
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val CODE_OK = "Ok"
        private const val CODE_NO_ROUTE = "NoRoute"
    }
}
