package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile

/**
 * Which backend the user has chosen (Settings → Routing server, ADR 0003).
 * MANAGED = Mockarr's keyed provider with the public servers as fallback;
 * PUBLIC = the public OSRM/Photon servers only; CUSTOM = the user's own OSRM URL.
 */
enum class BackendMode { MANAGED, PUBLIC, CUSTOM }

/**
 * Routes through [managed] when the mode is [BackendMode.MANAGED] and a managed
 * provider exists, falling through to [fallback] on any failure except a
 * definitive "no route". If both fail, the managed provider's error is reported
 * (it is the one that should have worked). Other modes go straight to [fallback],
 * which reads the custom URL from settings itself.
 */
class SwitchingRouteProvider(
    private val managed: RouteProvider?,
    private val fallback: RouteProvider,
    private val mode: () -> BackendMode,
) : RouteProvider {
    override suspend fun route(waypoints: List<LatLng>, profile: RoutingProfile): Result<Route> {
        val primary = managed?.takeIf { mode() == BackendMode.MANAGED }
            ?: return fallback.route(waypoints, profile)
        return routeWithFallback(primary, waypoints, profile)
    }

    private suspend fun routeWithFallback(
        primary: RouteProvider,
        waypoints: List<LatLng>,
        profile: RoutingProfile,
    ): Result<Route> {
        val first = primary.route(waypoints, profile)
        val error = first.exceptionOrNull()
        if (error == null || error is RoutingException.NoRoute) return first
        return fallback.route(waypoints, profile).fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(error) },
        )
    }
}

/** Same shape as [SwitchingRouteProvider] for place search and reverse lookup. */
class SwitchingGeocoder(
    private val managed: Geocoder?,
    private val fallback: Geocoder,
    private val mode: () -> BackendMode,
) : Geocoder {
    override suspend fun search(
        query: String,
        bias: LatLng?,
        zoom: Double?,
        limit: Int,
    ): Result<List<GeocodingResult>> = attempt({ it.search(query, bias, zoom, limit) })

    override suspend fun reverse(position: LatLng): Result<PlaceInfo> = attempt({ it.reverse(position) })

    private suspend fun <T> attempt(call: suspend (Geocoder) -> Result<T>): Result<T> {
        val primary = managed?.takeIf { mode() == BackendMode.MANAGED } ?: return call(fallback)
        val first = call(primary)
        val error = first.exceptionOrNull() ?: return first
        return call(fallback).fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(error) },
        )
    }
}

/** What the build knows about the managed backend; injected so UI can gate features on it. */
data class BackendConfig(val managedAvailable: Boolean)
