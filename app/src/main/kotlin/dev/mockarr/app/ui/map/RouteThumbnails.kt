package dev.mockarr.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import dev.mockarr.app.ui.theme.MapPalette
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.routing.Polyline6
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import org.maplibre.android.geometry.LatLng as MapLibreLatLng

/** Everything that identifies one thumbnail render (and its cache entry). */
data class ThumbSpec(
    val routeId: Long,
    val createdAtEpochMillis: Long,
    val encodedPolyline6: String,
    val styleUrl: String,
    val sizePx: Int,
    val density: Float,
    val palette: MapPalette,
)

/**
 * Renders saved-route thumbnails on the real basemap via [MapSnapshotter],
 * with the route drawn on top. Snapshots are cached in memory and as PNGs in
 * [Context.getCacheDir] (derived artifacts — the OS may evict them; the caller
 * keeps its own offline fallback). Failures return null and are remembered for
 * the session so offline scrolling doesn't re-hit the network per card.
 */
class RouteThumbnails(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val memory = LruCache<String, Bitmap>(MEMORY_ENTRIES)
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<Bitmap?>>()
    private val failedKeys = mutableSetOf<String>()

    // Each snapshotter spins its own render thread + GL context — cap the burst
    // a fast fling over many cards can create.
    private val snapshotPermits = Semaphore(MAX_CONCURRENT_SNAPSHOTS)

    suspend fun bitmapFor(spec: ThumbSpec): Bitmap? {
        val key = cacheFileName(spec)
        val cached = memory.get(key)
        if (cached != null) return cached
        val deferred = mutex.withLock {
            if (key in failedKeys) {
                null
            } else {
                // De-dupe: LazyColumn recycling must not launch two snapshotters
                // for one card. Work runs in the app scope so a card scrolling
                // off-screen still completes (and caches) the snapshot.
                inFlight.getOrPut(key) { scope.async { produce(spec, key) } }
            }
        }
        return deferred?.await()
    }

    /** Deletes cached files for routes that no longer exist. */
    suspend fun pruneExcept(liveRouteIds: Set<Long>) = withContext(Dispatchers.IO) {
        thumbDir().listFiles().orEmpty()
            .filter { routeIdOf(it.name) !in liveRouteIds }
            .forEach { it.delete() }
    }

    private suspend fun produce(spec: ThumbSpec, key: String): Bitmap? = try {
        val cached = readDisk(key)
        val bitmap = cached ?: generate(spec)
        if (bitmap == null) {
            mutex.withLock { failedKeys += key }
        } else {
            memory.put(key, bitmap)
            if (cached == null) writeDisk(key, bitmap)
        }
        bitmap
    } finally {
        mutex.withLock { inFlight.remove(key) }
    }

    private suspend fun generate(spec: ThumbSpec): Bitmap? {
        val points = overlayPoints(spec.encodedPolyline6)
        if (points.size < 2) return null
        val bounds = paddedBounds(points)
        val snapshot = snapshotPermits.withPermit {
            withTimeoutOrNull(SNAPSHOT_TIMEOUT_MILLIS) {
                withContext(Dispatchers.Main) { takeSnapshot(spec, bounds) }
            }
        }
        return snapshot?.let {
            withContext(Dispatchers.Default) { drawRouteOverlay(it, points, spec.density, spec.palette) }
        }
    }

    // MapSnapshotter must be created on a Looper thread.
    private suspend fun takeSnapshot(spec: ThumbSpec, bounds: BoundsBox): MapSnapshot? =
        suspendCancellableCoroutine { cont ->
            val snapshotter = MapSnapshotter(
                context,
                MapSnapshotter.Options(spec.sizePx, spec.sizePx)
                    .withStyleBuilder(Style.Builder().fromUri(spec.styleUrl))
                    .withRegion(bounds.toMapLibre())
                    .withLogo(false)
                    .withPixelRatio(1f),
            )
            snapshotter.start(
                { snapshot -> cont.resume(snapshot) },
                { cont.resume(null) },
            )
            cont.invokeOnCancellation { snapshotter.cancel() }
        }

    private suspend fun readDisk(key: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(thumbDir(), key)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    private suspend fun writeDisk(key: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = thumbDir().apply { mkdirs() }
            val tmp = File(dir, "$key.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            tmp.renameTo(File(dir, key))
        }
    }

    private fun thumbDir() = File(context.cacheDir, THUMB_DIR)

    private companion object {
        const val THUMB_DIR = "route_thumbs"
        const val MEMORY_ENTRIES = 16
        const val MAX_CONCURRENT_SNAPSHOTS = 2
        const val SNAPSHOT_TIMEOUT_MILLIS = 10_000L
    }
}

/** Pure lat/lng box, kept MapLibre-free so the padding math is unit-testable. */
data class BoundsBox(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
) {
    fun toMapLibre(): LatLngBounds = LatLngBounds.from(maxLat, maxLng, minLat, minLng)
}

/**
 * The route's bounding box padded [BOUNDS_PADDING_FRACTION] per side, with a
 * minimum span so a very short route doesn't snapshot a single building at
 * extreme zoom.
 */
internal fun paddedBounds(points: List<LatLng>): BoundsBox {
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLng = points.minOf { it.longitude }
    val maxLng = points.maxOf { it.longitude }
    val latSpan = max((maxLat - minLat) * (1 + 2 * BOUNDS_PADDING_FRACTION), MIN_SPAN_DEGREES)
    val lngSpan = max((maxLng - minLng) * (1 + 2 * BOUNDS_PADDING_FRACTION), MIN_SPAN_DEGREES)
    val midLat = (minLat + maxLat) / 2
    val midLng = (minLng + maxLng) / 2
    return BoundsBox(
        minLat = (midLat - latSpan / 2).coerceAtLeast(-MAX_ABS_LATITUDE),
        maxLat = (midLat + latSpan / 2).coerceAtMost(MAX_ABS_LATITUDE),
        minLng = midLng - lngSpan / 2,
        maxLng = midLng + lngSpan / 2,
    )
}

internal fun cacheFileName(spec: ThumbSpec): String {
    // Style AND palette: the overlay colours are baked into the PNG, so a theme
    // switch must miss the cache rather than serve the other theme's route line.
    val styleHash = spec.styleUrl.hashCode().toUInt().toString(RADIX_HEX)
    val paletteHash = spec.palette.hashCode().toUInt().toString(RADIX_HEX)
    return "r${spec.routeId}_${spec.createdAtEpochMillis}_${styleHash}_${paletteHash}_${spec.sizePx}.png"
}

/** Route id encoded in a cache filename by [cacheFileName], or null. */
internal fun routeIdOf(fileName: String): Long? =
    fileName.removePrefix("r").substringBefore('_').toLongOrNull()

private fun overlayPoints(encodedPolyline6: String): List<LatLng> {
    val decoded = runCatching { Polyline6.decode(encodedPolyline6) }.getOrDefault(emptyList())
    if (decoded.size <= MAX_OVERLAY_POINTS) return decoded
    return List(MAX_OVERLAY_POINTS) { decoded[it * (decoded.size - 1) / (MAX_OVERLAY_POINTS - 1)] }
}

private fun drawRouteOverlay(
    snapshot: MapSnapshot,
    points: List<LatLng>,
    density: Float,
    palette: MapPalette,
): Bitmap {
    val bitmap = snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(bitmap)
    val path = Path()
    points.forEachIndexed { index, p ->
        val px = snapshot.pixelForLatLng(MapLibreLatLng(p.latitude, p.longitude))
        if (index == 0) path.moveTo(px.x, px.y) else path.lineTo(px.x, px.y)
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // Casing first so the line reads over any basemap color.
    stroke.color = palette.routeCasing
    stroke.strokeWidth = HALO_WIDTH_DP * density
    canvas.drawPath(path, stroke)
    stroke.color = palette.route
    stroke.strokeWidth = ROUTE_WIDTH_DP * density
    canvas.drawPath(path, stroke)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    listOf(points.first() to palette.stopStart, points.last() to palette.stopEnd).forEach { (point, color) ->
        val px = snapshot.pixelForLatLng(MapLibreLatLng(point.latitude, point.longitude))
        dot.color = palette.routeCasing
        canvas.drawCircle(px.x, px.y, (DOT_RADIUS_DP + DOT_STROKE_DP) * density, dot)
        dot.color = color
        canvas.drawCircle(px.x, px.y, DOT_RADIUS_DP * density, dot)
    }
    return bitmap
}

private const val BOUNDS_PADDING_FRACTION = 0.25
private const val MIN_SPAN_DEGREES = 0.004
private const val MAX_ABS_LATITUDE = 85.0
private const val MAX_OVERLAY_POINTS = 200

private const val RADIX_HEX = 16
private const val ROUTE_WIDTH_DP = 3f
private const val HALO_WIDTH_DP = 4.5f
private const val DOT_RADIUS_DP = 3.5f
private const val DOT_STROKE_DP = 1f
