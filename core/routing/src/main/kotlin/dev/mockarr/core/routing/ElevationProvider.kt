package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng

/**
 * Terrain elevation lookups, provider-neutral (see [Geocoder] for why the app
 * binds to the interface rather than a client).
 */
interface ElevationProvider {
    /** Elevation in meters for each input coordinate, in order. */
    suspend fun elevations(coordinates: List<LatLng>): Result<List<Double>>
}
