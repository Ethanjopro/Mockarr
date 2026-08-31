package dev.mockarr.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mockarr.app.R
import dev.mockarr.app.ui.rememberFormatter
import dev.mockarr.app.ui.theme.DialogAction
import dev.mockarr.app.ui.theme.MapPalette
import dev.mockarr.app.ui.theme.MockarrDialog
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.data.SavedRouteEntity
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.RoutingProfile
import dev.mockarr.core.routing.Polyline6
import kotlin.math.cos
import kotlin.math.max

typealias ThumbnailLoader = suspend (SavedRouteEntity, String, Int, Float, MapPalette) -> ImageBitmap?

/**
 * Strava's saved-route card, in Material: thumbnail left, a two-line title, a
 * mode chip + distance · duration, the place, when it was created, and an
 * overflow for rename / delete. Tapping the card loads the route.
 */
@Composable
fun SavedRouteCard(
    entity: SavedRouteEntity,
    units: DistanceUnits,
    thumbStyleUrl: String,
    palette: MapPalette,
    nowEpochMillis: Long,
    totalDurationSeconds: Double,
    loadThumbnail: ThumbnailLoader,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = rememberFormatter()
    val split = remember(entity.name) { splitRouteName(entity.name) }
    val profile = RoutingProfile.fromNameOrDefault(entity.profile)
    Card(
        onClick = onClick,
        shape = Tokens.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = Tokens.space3, top = Tokens.space3, bottom = Tokens.space3),
            verticalAlignment = Alignment.Top,
        ) {
            MapThumbnail(
                entity = entity,
                styleUrl = thumbStyleUrl,
                palette = palette,
                loadThumbnail = loadThumbnail,
            )
            Spacer(Modifier.width(Tokens.space3))
            Column(modifier = Modifier.weight(1f).padding(top = Tokens.space1)) {
                Text(
                    text = split.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Tokens.space1))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModePill(profile = profile)
                    Spacer(Modifier.width(Tokens.space2))
                    Text(
                        text = stringResource(
                            R.string.routes_card_meta,
                            formatter.distance(entity.distanceMeters, units),
                            formatter.duration(totalDurationSeconds),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                split.place?.let { place ->
                    Text(
                        text = place,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = createdText(entity.createdAtEpochMillis, nowEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CardOverflow(name = split.title, onRename = onRename, onDelete = onDelete)
        }
    }
}

/** Read-only mode tag: outlined pill, never a button (a disabled chip reads as broken). */
@Composable
private fun ModePill(profile: RoutingProfile) {
    val shape = RoundedCornerShape(CHIP_HEIGHT / 2)
    Row(
        modifier = Modifier
            .height(CHIP_HEIGHT)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.space1),
    ) {
        Icon(
            painter = painterResource(profile.iconRes()),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(CHIP_ICON),
        )
        Text(
            text = stringResource(profile.shortLabelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun createdText(epochMillis: Long, nowEpochMillis: Long): String =
    when (val created = remember(epochMillis, nowEpochMillis) { createdWhen(epochMillis, nowEpochMillis) }) {
        CreatedWhen.Today -> stringResource(R.string.routes_created_today)
        CreatedWhen.Yesterday -> stringResource(R.string.routes_created_yesterday)
        is CreatedWhen.OnDate -> stringResource(R.string.routes_created_on, created.formatted)
    }

@Composable
private fun CardOverflow(name: String, onRename: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more),
                contentDescription = stringResource(R.string.routes_more_cd, name),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.routes_rename)) },
                onClick = {
                    open = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.routes_delete), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
fun RenameRouteDialog(initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    MockarrDialog(
        title = stringResource(R.string.routes_rename_title),
        onDismissRequest = onDismiss,
        confirm = DialogAction(
            label = stringResource(R.string.dialog_save),
            onClick = { onConfirm(name) },
            enabled = name.isNotBlank(),
        ),
        dismiss = DialogAction(stringResource(R.string.dialog_cancel), onDismiss),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.routes_rename_name)) },
            singleLine = true,
        )
    }
}

/** Strava's "No results" page: one glyph, one line, one action. */
@Composable
fun RoutesEmptyState(hasAnyRoutes: Boolean, onPlanDrive: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Tokens.space6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_route),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(EMPTY_GLYPH),
        )
        Spacer(Modifier.height(Tokens.space4))
        Text(
            text = stringResource(if (hasAnyRoutes) R.string.routes_no_match_title else R.string.routes_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Tokens.space2))
        Text(
            text = stringResource(if (hasAnyRoutes) R.string.routes_no_match_body else R.string.routes_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!hasAnyRoutes) {
            Spacer(Modifier.height(Tokens.space6))
            Button(onClick = onPlanDrive) {
                Icon(painterResource(R.drawable.ic_add_route), contentDescription = null)
                Spacer(Modifier.width(Tokens.space2))
                Text(stringResource(R.string.routes_empty_cta))
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
    loadThumbnail: ThumbnailLoader,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { THUMB_SIZE.roundToPx() }
    val thumb by produceState<ImageBitmap?>(null, entity.id, styleUrl, palette) {
        value = loadThumbnail(entity, styleUrl, sizePx, density.density, palette)
    }
    val snapshot = thumb
    if (snapshot != null) {
        Image(
            bitmap = snapshot,
            contentDescription = stringResource(R.string.routes_thumbnail_cd, entity.name),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(THUMB_SIZE).clip(Tokens.controlShape),
        )
    } else {
        RouteGlyph(encodedPolyline6 = entity.encodedPolyline6)
    }
}

/** The route's shape drawn as a small glyph — instant, offline identification. */
@Composable
private fun RouteGlyph(encodedPolyline6: String, modifier: Modifier = Modifier) {
    val points = remember(encodedPolyline6) { thumbnailPoints(encodedPolyline6) }
    val pathColor = MaterialTheme.colorScheme.primary
    val startColor = Color(MockarrTheme.colors.map.stopStart)
    val endColor = Color(MockarrTheme.colors.map.stopEnd)
    Canvas(
        modifier = modifier
            .size(THUMB_SIZE)
            .clip(Tokens.controlShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
        val span = max(max(width, height), MIN_SPAN)
        val pad = THUMB_PADDING.toPx()
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
            style = Stroke(width = GLYPH_STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(startColor, radius = GLYPH_DOT.toPx(), center = toOffset(points.first()))
        drawCircle(endColor, radius = GLYPH_DOT.toPx(), center = toOffset(points.last()))
    }
}

private fun thumbnailPoints(encodedPolyline6: String): List<LatLng> {
    val decoded = runCatching { Polyline6.decode(encodedPolyline6) }.getOrDefault(emptyList())
    if (decoded.size <= MAX_THUMB_POINTS) return decoded
    return List(MAX_THUMB_POINTS) { decoded[it * (decoded.size - 1) / (MAX_THUMB_POINTS - 1)] }
}

private val THUMB_SIZE = 88.dp
private val THUMB_PADDING = Tokens.space2
private val GLYPH_STROKE = 3.dp
private val GLYPH_DOT = 3.5.dp
private val CHIP_HEIGHT = Tokens.space6
private val CHIP_ICON = Tokens.space4
private val EMPTY_GLYPH = 56.dp
private const val MAX_THUMB_POINTS = 64
private const val MIN_SPAN = 1e-9
