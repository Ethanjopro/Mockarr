package dev.mockarr.core.routing

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RouteLeg
import dev.mockarr.core.model.RoutingProfile
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

/**
 * [RouteProvider] backed by the Geoapify Routing API (ADR 0003). Geoapify
 * returns timing per *step* (a stretch between manoeuvres); the provider spreads
 * each step's time over its geometry segments in proportion to segment length so
 * the realism engine sees the per-segment [RouteLeg] it expects.
 */
class GeoapifyRouteProvider(
    private val apiKey: String,
    userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : RouteProvider {

    private interface GeoapifyApi {
        @GET
        suspend fun route(@Url url: String): GeoapifyResponse
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

    override suspend fun route(waypoints: List<LatLng>, profile: RoutingProfile): Result<Route> {
        require(waypoints.size >= 2) { "At least two waypoints are required" }
        val url = routeUrl(baseUrl, apiKey, waypoints, profile)
        return try {
            val feature = api.route(url).features.firstOrNull()
                ?: return Result.failure(RoutingException.NoRoute())
            Result.success(feature.toRoute())
        } catch (e: HttpException) {
            Result.failure(e.toRoutingException())
        } catch (e: IOException) {
            Result.failure(RoutingException.Network(e))
        }
    }

    private fun HttpException.toRoutingException(): RoutingException {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val message = body?.let { runCatching { json.decodeFromString<GeoapifyError>(it).message }.getOrNull() }
        return when {
            code() == HTTP_TOO_MANY_REQUESTS -> RoutingException.RateLimited()
            code() == HTTP_BAD_REQUEST && message.isNoRoute() -> RoutingException.NoRoute()
            else -> RoutingException.Server("HTTP ${code()}${message?.let { ": $it" }.orEmpty()}")
        }
    }

    /** "Route not found", or "No suitable edges near location" — a stop too far from any road. */
    private fun String?.isNoRoute(): Boolean =
        this != null && (contains("route", ignoreCase = true) || contains("suitable edges", ignoreCase = true))

    @Serializable
    private data class GeoapifyResponse(val features: List<GeoapifyFeature> = emptyList())

    @Serializable
    private data class GeoapifyError(val message: String? = null)

    @Serializable
    private data class GeoapifyFeature(
        val properties: GeoapifyProperties = GeoapifyProperties(),
        val geometry: GeoapifyGeometry = GeoapifyGeometry(),
    ) {
        fun toRoute(): Route {
            val legLines = geometry.coordinates.map { line -> line.map { LatLng(it[1], it[0]) } }
            val points = joinLegLines(legLines)
            val legs = properties.legs.mapIndexed { index, leg ->
                spreadSteps(legLines.getOrElse(index) { emptyList() }, leg)
            }
            return Route(
                points = points,
                legs = legs,
                distanceMeters = properties.distance,
                durationSeconds = properties.time,
                snappedWaypoints = snappedWaypoints(legLines),
            )
        }
    }

    @Serializable
    private data class GeoapifyProperties(
        val distance: Double = 0.0,
        val time: Double = 0.0,
        val legs: List<GeoapifyLeg> = emptyList(),
    )

    @Serializable
    private data class GeoapifyLeg(
        val distance: Double = 0.0,
        val time: Double = 0.0,
        val steps: List<GeoapifyStep> = emptyList(),
    )

    @Serializable
    private data class GeoapifyStep(
        @SerialName("from_index") val fromIndex: Int = 0,
        @SerialName("to_index") val toIndex: Int = 0,
        val distance: Double = 0.0,
        val time: Double = 0.0,
    )

    /** GeoJSON MultiLineString: one line of `[lon, lat]` pairs per leg. */
    @Serializable
    private data class GeoapifyGeometry(val coordinates: List<List<List<Double>>> = emptyList())

    companion object {
        const val DEFAULT_BASE_URL = "https://api.geoapify.com"
        private const val FALLBACK_BASE_URL = "$DEFAULT_BASE_URL/"
        private const val MIN_REQUEST_INTERVAL_MILLIS = 250L
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_TOO_MANY_REQUESTS = 429

        /** The request Geoapify receives; public so tests pin the parameters. */
        fun routeUrl(baseUrl: String, apiKey: String, waypoints: List<LatLng>, profile: RoutingProfile): String =
            buildString {
                append(baseUrl.trimEnd('/'))
                append("/v1/routing?waypoints=")
                waypoints.joinTo(this, "|") { "${it.latitude},${it.longitude}" }
                append("&mode=")
                append(profile.geoapifyMode)
                append("&format=geojson&apiKey=")
                append(apiKey)
            }

        private val RoutingProfile.geoapifyMode: String
            get() = when (this) {
                RoutingProfile.DRIVING -> "drive"
                RoutingProfile.WALKING -> "walk"
                RoutingProfile.CYCLING -> "bicycle"
            }

        /**
         * Where the router actually put each stop. Geoapify's `properties.waypoints`
         * echo the *requested* coordinates, so they are useless for detecting an
         * off-road stop ([stitchOffRoad] compares against them); the leg lines,
         * however, start and end on the road, so their end points are the snaps.
         */
        internal fun snappedWaypoints(legLines: List<List<LatLng>>): List<LatLng> {
            val lines = legLines.filter { it.isNotEmpty() }
            val first = lines.firstOrNull()?.first() ?: return emptyList()
            return listOf(first) + lines.map { it.last() }
        }

        /**
         * Consecutive legs share their junction point; drop the duplicate so the
         * route is one continuous polyline with `sum(leg segments)` segments.
         */
        internal fun joinLegLines(legLines: List<List<LatLng>>): List<LatLng> {
            val points = ArrayList<LatLng>()
            for (line in legLines) {
                val start = if (points.isNotEmpty() && line.isNotEmpty() && points.last() == line.first()) 1 else 0
                points.addAll(line.subList(minOf(start, line.size), line.size))
            }
            return points
        }

        /**
         * Per-segment distances/durations for one leg: every segment's duration
         * is its step's time weighted by the segment's share of the step's length.
         * Segments no step claims (never in practice) inherit the leg's mean speed.
         */
        internal fun spreadSteps(line: List<LatLng>, leg: GeoapifyLegData): RouteLeg {
            if (line.size < 2) return RouteLeg(emptyList(), emptyList())
            val distances = DoubleArray(line.size - 1) { GeoMath.distanceMeters(line[it], line[it + 1]) }
            val durations = DoubleArray(line.size - 1) { Double.NaN }
            for (step in leg.steps) {
                val from = step.fromIndex.coerceIn(0, line.size - 1)
                val to = step.toIndex.coerceIn(from, line.size - 1)
                if (to == from) continue
                val stepLength = (from until to).sumOf { distances[it] }
                for (i in from until to) {
                    durations[i] = if (stepLength > 0) {
                        step.time * distances[i] / stepLength
                    } else {
                        step.time / (to - from)
                    }
                }
            }
            val meanSpeed = leg.distance.takeIf { it > 0 }?.let { leg.time / it } ?: 0.0
            for (i in durations.indices) {
                if (durations[i].isNaN()) durations[i] = distances[i] * meanSpeed
            }
            return RouteLeg(distances.toList(), durations.toList())
        }

        private fun spreadSteps(line: List<LatLng>, leg: GeoapifyLeg): RouteLeg {
            val steps = leg.steps.map { GeoapifyStepData(it.fromIndex, it.toIndex, it.time) }
            return spreadSteps(line, GeoapifyLegData(leg.distance, leg.time, steps))
        }
    }
}

/** Provider-neutral view of a Geoapify leg so the spreading logic is unit-testable. */
data class GeoapifyLegData(val distance: Double, val time: Double, val steps: List<GeoapifyStepData>)

data class GeoapifyStepData(val fromIndex: Int, val toIndex: Int, val time: Double)
