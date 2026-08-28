package dev.mockarr.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mockarr.app.ui.formatRouteTimestamp
import dev.mockarr.app.ui.label
import dev.mockarr.app.ui.map.effectiveStyleUrl
import dev.mockarr.app.ui.routeSummaryText
import dev.mockarr.app.ui.theme.MapPalette
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.core.data.SavedRouteEntity
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.routing.Polyline6
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max

@Composable
fun SavedRoutesScreen(
    onRouteLoaded: () -> Unit,
    viewModel: SavedRoutesViewModel = hiltViewModel(),
) {
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    val mapStyleUrl by viewModel.mapStyleUrl.collectAsStateWithLifecycle()
    val thumbStyleUrl = effectiveStyleUrl(mapStyleUrl, isSystemInDarkTheme())
    val palette = MockarrTheme.colors.map
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (routes.isNotEmpty() || query.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Search saved routes") },
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (routes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (query.isBlank()) "No saved routes yet" else "No routes match \"$query\"",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    if (query.isBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Build a route on the Map tab and tap Save. " +
                                "Saved routes replay offline.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(routes, key = { it.id }) { entity ->
                        SavedRouteCard(
                            entity = entity,
                            units = units,
                            thumbStyleUrl = thumbStyleUrl,
                            palette = palette,
                            loadThumbnail = viewModel::thumbnail,
                            onClick = {
                                viewModel.load(entity)
                                onRouteLoaded()
                            },
                            onDelete = {
                                viewModel.delete(entity)
                                scope.launch {
                                    val result = snackbarHost.showSnackbar(
                                        message = "Deleted \"${entity.name}\"",
                                        actionLabel = "Undo",
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restore(entity)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SavedRouteCard(
    entity: SavedRouteEntity,
    units: DistanceUnits,
    thumbStyleUrl: String,
    palette: MapPalette,
    loadThumbnail: suspend (SavedRouteEntity, String, Int, Float, MapPalette) -> ImageBitmap?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapThumbnail(
                entity = entity,
                styleUrl = thumbStyleUrl,
                palette = palette,
                loadThumbnail = loadThumbnail,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "%s · %s · %s".format(
                        routeSummaryText(entity.distanceMeters, entity.durationSeconds, units),
                        RoutingProfile.fromNameOrDefault(entity.profile).label(),
                        formatRouteTimestamp(entity.createdAtEpochMillis),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${entity.name}")
            }
        }
    }
}

/**
 * Real-basemap snapshot of the route when available; the offline glyph fills in
 * while the snapshot loads and stays whenever it can't be generated.
 */
@Composable
private fun MapThumbnail(
    entity: SavedRouteEntity,
    styleUrl: String,
    palette: MapPalette,
    loadThumbnail: suspend (SavedRouteEntity, String, Int, Float, MapPalette) -> ImageBitmap?,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { THUMB_SIZE_DP.dp.roundToPx() }
    val thumb by produceState<ImageBitmap?>(null, entity.id, styleUrl, palette) {
        value = loadThumbnail(entity, styleUrl, sizePx, density.density, palette)
    }
    val snapshot = thumb
    if (snapshot != null) {
        Image(
            bitmap = snapshot,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(THUMB_SIZE_DP.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        RouteThumbnail(encodedPolyline6 = entity.encodedPolyline6)
    }
}

/** The route's shape drawn as a small glyph — instant, offline identification. */
@Composable
private fun RouteThumbnail(encodedPolyline6: String, modifier: Modifier = Modifier) {
    val points = remember(encodedPolyline6) { thumbnailPoints(encodedPolyline6) }
    val pathColor = MaterialTheme.colorScheme.primary
    val startColor = Color(MockarrTheme.colors.map.stopStart)
    val endColor = Color(MockarrTheme.colors.map.stopEnd)
    Canvas(
        modifier = modifier
            .size(THUMB_SIZE_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }
        // Longitude degrees shrink with latitude — scale so shapes keep aspect.
        val cosLat = cos(Math.toRadians((minLat + maxLat) / 2))
        val width = (maxLng - minLng) * cosLat
        val height = maxLat - minLat
        val span = max(max(width, height), 1e-9)
        val pad = THUMB_PADDING_DP.dp.toPx()
        val box = size.minDimension - 2 * pad

        fun toOffset(p: LatLng) = Offset(
            x = (pad + ((p.longitude - minLng) * cosLat + (span - width) / 2) / span * box).toFloat(),
            y = (pad + ((maxLat - p.latitude) + (span - height) / 2) / span * box).toFloat(),
        )

        val path = Path()
        points.forEachIndexed { index, p ->
            val offset = toOffset(p)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        drawPath(
            path = path,
            color = pathColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(startColor, radius = 3.5.dp.toPx(), center = toOffset(points.first()))
        drawCircle(endColor, radius = 3.5.dp.toPx(), center = toOffset(points.last()))
    }
}

private fun thumbnailPoints(encodedPolyline6: String): List<LatLng> {
    val decoded = runCatching { Polyline6.decode(encodedPolyline6) }.getOrDefault(emptyList())
    if (decoded.size <= MAX_THUMB_POINTS) return decoded
    return List(MAX_THUMB_POINTS) { decoded[it * (decoded.size - 1) / (MAX_THUMB_POINTS - 1)] }
}

private const val THUMB_SIZE_DP = 88
private const val THUMB_PADDING_DP = 8
private const val MAX_THUMB_POINTS = 64
