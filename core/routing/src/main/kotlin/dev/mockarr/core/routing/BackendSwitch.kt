package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.Route
import dev.mockarr.core.model.RoutingProfile

/**
 * Routes through [managed] when the build has one (ADR 0003), falling through
 * to [fallback] on *any* failure — including "no route": the public OSRM's
 * `snapping=any` reaches stops the managed router refuses, and the off-road
 * stitcher takes it from there. If both fail, a definitive "no route" from the
 * fallback wins; otherwise the managed provider's error is reported (it is the
 * one that should have worked). No managed provider = [fallback] only.
 */
class SwitchingRouteProvider(
    private val managed: RouteProvider?,
    private val fallback: RouteProvider,
) : RouteProvider {
    override suspend fun route(waypoints: List<LatLng>, profile: RoutingProfile): Result<Route> {
        val primary = managed ?: return fallback.route(waypoints, profile)
        val first = primary.route(waypoints, profile)
        val error = first.exceptionOrNull() ?: return first
        return fallback.route(waypoints, profile).fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(if (it is RoutingException.NoRoute) it else error) },
        )
    }
}

/** Same shape as [SwitchingRouteProvider] for place search and reverse lookup. */
class SwitchingGeocoder(
    private val managed: Geocoder?,
    private val fallback: Geocoder,
) : Geocoder {
    override suspend fun search(
        query: String,
        bias: LatLng?,
        zoom: Double?,
        limit: Int,
    ): Result<List<GeocodingResult>> = attempt({ it.search(query, bias, zoom, limit) })

    override suspend fun reverse(position: LatLng): Result<PlaceInfo> = attempt({ it.reverse(position) })

    private suspend fun <T> attempt(call: suspend (Geocoder) -> Result<T>): Result<T> {
        val primary = managed ?: return call(fallback)
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
