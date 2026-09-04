package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * [ElevationProvider] reading Mapzen/AWS "Terrarium" terrain tiles (ADR 0003):
 * public, keyless PNG tiles where `elevation = R*256 + G + B/256 - 32768`. One
 * z12 tile spans ~10 km, so a route costs a handful of tile fetches instead of
 * a request per 100 points; decoded tiles are kept in a small LRU.
 */
class TerrariumElevationProvider(
    userAgent: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val zoom: Int = DEFAULT_ZOOM,
    cacheTiles: Int = DEFAULT_CACHE_TILES,
) : ElevationProvider {

    private val client = OkHttpClient.Builder()
        .addInterceptor(UserAgentInterceptor(userAgent))
        .build()

    private val cache = object : LinkedHashMap<TileKey, RgbImage>(cacheTiles, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, RgbImage>?): Boolean = size > cacheTiles
    }

    override suspend fun elevations(coordinates: List<LatLng>): Result<List<Double>> {
        if (coordinates.isEmpty()) return Result.success(emptyList())
        return withContext(Dispatchers.IO) {
            runCatching {
                coordinates.map { position ->
                    val (tile, px, py) = locate(position, zoom)
                    val image = tileFor(tile)
                    elevationOf(image, px, py)
                }
            }
        }
    }

    private fun tileFor(key: TileKey): RgbImage {
        synchronized(cache) { cache[key] }?.let { return it }
        val request = Request.Builder().url(tileUrl(baseUrl, key)).build()
        val bytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Terrain tile ${key.z}/${key.x}/${key.y}: HTTP ${response.code}")
            }
            response.body?.bytes() ?: throw IOException("Terrain tile ${key.z}/${key.x}/${key.y}: empty body")
        }
        val image = Png.decode(bytes)
        synchronized(cache) { cache[key] = image }
        return image
    }

    data class TileKey(val z: Int, val x: Int, val y: Int)

    /** A tile plus the pixel inside it for a coordinate. */
    data class TilePixel(val tile: TileKey, val px: Int, val py: Int)

    companion object {
        const val DEFAULT_BASE_URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium"
        const val DEFAULT_ZOOM = 12
        const val TILE_SIZE = 256
        private const val DEFAULT_CACHE_TILES = 24
        private const val LOAD_FACTOR = 0.75f
        private const val TERRARIUM_OFFSET = 32768.0
        private const val BYTE_RANGE = 256.0
        private const val MAX_LATITUDE = 85.0511

        fun tileUrl(baseUrl: String, key: TileKey): String = "${baseUrl.trimEnd('/')}/${key.z}/${key.x}/${key.y}.png"

        /** Web-Mercator tile + pixel for [position] at [zoom]. */
        fun locate(position: LatLng, zoom: Int): TilePixel {
            val n = 1 shl zoom
            val lat = position.latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
            val xTile = (position.longitude + 180.0) / 360.0 * n
            val latRad = Math.toRadians(lat)
            val yTile = (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n
            val x = floor(xTile).toInt().coerceIn(0, n - 1)
            val y = floor(yTile).toInt().coerceIn(0, n - 1)
            val px = ((xTile - x) * TILE_SIZE).toInt().coerceIn(0, TILE_SIZE - 1)
            val py = ((yTile - y) * TILE_SIZE).toInt().coerceIn(0, TILE_SIZE - 1)
            return TilePixel(TileKey(zoom, x, y), px, py)
        }

        /** Inverse of [locate] for a tile's north-west corner — used by tests. */
        fun tileOrigin(key: TileKey): LatLng {
            val n = 1 shl key.z
            val lon = key.x.toDouble() / n * 360.0 - 180.0
            val lat = Math.toDegrees(atan(sinh(PI * (1 - 2.0 * key.y / n))))
            return LatLng(lat, lon)
        }

        fun elevationOf(image: RgbImage, px: Int, py: Int): Double {
            val red = image.channel(px, py, 0)
            val green = image.channel(px, py, 1)
            val blue = image.channel(px, py, 2)
            return red * BYTE_RANGE + green + blue / BYTE_RANGE - TERRARIUM_OFFSET
        }
    }
}
