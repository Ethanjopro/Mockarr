package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mockarr.app.R
import dev.mockarr.app.ui.formatDistance
import dev.mockarr.app.ui.formatDurationShort
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.DistanceUnits
import kotlin.math.roundToInt

/** What the strip says about the session, in colour. */
enum class StripTone { Neutral, Ready, Accent, Hold, Error }

/**
 * Strava's Record "run box": a card floating over the map above the sheet —
 * the status strip on top, the stat trio (and progress) below when there is
 * something to count. [stats] null hides the trio: undefined numbers are
 * never shown as placeholders. The slot is its own recomposition scope, so a
 * per-fix speed cell does not redraw the whole overlay.
 */
@Composable
internal fun StatCard(
    strip: StripModel,
    stats: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    onStripAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Tokens.cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = Tokens.cardElevation,
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            StatusStrip(
                text = strip.text,
                tone = strip.tone,
                actionLabel = strip.actionLabel,
                onAction = onStripAction,
                trailing = trailing,
            )
            AnimatedVisibility(visible = stats != null) {
                Column(modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space3)) {
                    stats?.invoke()
                }
            }
        }
    }
}

/**
 * The card's top band: one line of state copy on a tinted colour that
 * crossfades between states, so a transition reads as one surface changing.
 */
@Composable
fun StatusStrip(
    text: String,
    tone: StripTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MockarrTheme.colors
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        StripTone.Neutral -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        StripTone.Ready -> colors.readyContainer to colors.onReadyContainer
        StripTone.Accent -> scheme.primaryContainer to scheme.onPrimaryContainer
        StripTone.Hold -> colors.holdContainer to colors.onHoldContainer
        StripTone.Error -> scheme.errorContainer to scheme.onErrorContainer
    }
    val background by animateColorAsState(container, label = "stripBackground")
    val foreground by animateColorAsState(content, label = "stripForeground")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = Tokens.inset)
            .heightIn(min = STRIP_MIN_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = foreground,
            textAlign = if (actionLabel == null && trailing == null) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(Tokens.space2))
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = foreground),
            ) { Text(actionLabel) }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Tokens.space2))
            trailing()
        }
    }
}

/** One value-over-label cell of the stat trio. */
data class StatCell(val label: String, val value: String)

/** Three equal cells: bold tabular value with its label under it (Strava's trio); no hero numeral. */
@Composable
fun StatTrio(cells: List<StatCell>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = cell.value,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = cell.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Progress under the trio: thin, accent, no label — the trio already says the numbers. */
@Composable
fun DriveProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(PROGRESS_HEIGHT),
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {},
    )
}

/** The loaded route's Distance · Duration · Stops, or null when nothing is loaded. */
@Composable
internal fun recordCells(state: MapViewModel.UiState, units: DistanceUnits): List<StatCell>? {
    val route = state.route ?: return null
    val seconds = route.durationSeconds * state.trafficFactor + route.waypointWaitsSeconds.sum()
    val minutes = (seconds / SECONDS_PER_MINUTE).roundToInt().coerceAtLeast(1)
    return listOf(
        StatCell(stringResource(R.string.stat_distance), formatDistance(route.distanceMeters, units)),
        StatCell(
            stringResource(R.string.stat_duration),
            formatDurationShort(minutes * SECONDS_PER_MINUTE.toDouble()),
        ),
        StatCell(stringResource(R.string.stat_stops), state.waypoints.size.toString()),
    )
}

private const val SECONDS_PER_MINUTE = 60
private val STRIP_MIN_HEIGHT = 48.dp
private val PROGRESS_HEIGHT = 4.dp
