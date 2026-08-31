package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mockarr.app.R
import dev.mockarr.app.ui.rememberFormatter
import dev.mockarr.app.ui.theme.MapPopover
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.DistanceUnits
import kotlin.math.roundToInt

/** What the strip says about the session, in colour. */
enum class StripTone { Neutral, Ready, Accent, Hold, Error }

/**
 * Strava's Record "run box": a card floating over the map above the sheet —
 * one status strip on top (the session's single band: idle prompt, ready,
 * driving, arrived, holding, error) and the stat trio (with progress) below
 * when there is something to count. [stats] null hides the trio: undefined
 * numbers are never shown as placeholders. The slot is its own recomposition
 * scope, so a per-fix speed cell does not redraw the whole overlay.
 */
@Composable
internal fun StatCard(
    strip: StripModel,
    stats: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    onStripAction: (() -> Unit)? = null,
    onStatsClick: (() -> Unit)? = null,
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
                // Only the stats block is tappable — the strip keeps its own action.
                val editLabel = stringResource(R.string.stat_trio_edit_cd)
                val clickable = if (onStatsClick != null) {
                    Modifier
                        .clickable(onClick = onStatsClick, role = Role.Button)
                        .semantics { contentDescription = editLabel }
                } else {
                    Modifier
                }
                Column(modifier = clickable.padding(horizontal = Tokens.inset, vertical = Tokens.space3)) {
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
            .heightIn(min = Tokens.touchTarget),
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

/** The speed presets in a card floated above the run box's speed pill. */
@Composable
internal fun SpeedPopover(
    anchor: Offset,
    speedMultiplier: Double,
    onSpeedChange: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    MapPopover(anchor = anchor, onDismiss = onDismiss) {
        SpeedChips(
            speedMultiplier = speedMultiplier,
            // Menu semantics: pick, apply, close — the pill's label confirms it.
            onSpeedChange = {
                onSpeedChange(it)
                onDismiss()
            },
            modifier = Modifier.padding(horizontal = Tokens.space4, vertical = Tokens.space2),
        )
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
                    softWrap = false,
                    // "1 h 12 min" overflows a third of the card at 28sp: shrink, never clip.
                    autoSize = TextAutoSize.StepBased(minFontSize = TRIO_MIN_FONT, maxFontSize = TRIO_MAX_FONT),
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
    val formatter = rememberFormatter()
    val seconds = route.durationSeconds * state.trafficFactor + route.waypointWaitsSeconds.sum()
    val minutes = (seconds / SECONDS_PER_MINUTE).roundToInt().coerceAtLeast(1)
    return listOf(
        StatCell(stringResource(R.string.stat_distance), formatter.distance(route.distanceMeters, units)),
        StatCell(
            stringResource(R.string.stat_duration),
            formatter.duration(minutes * SECONDS_PER_MINUTE.toDouble()),
        ),
        StatCell(stringResource(R.string.stat_stops), state.waypoints.size.toString()),
    )
}

private const val SECONDS_PER_MINUTE = 60
private val TRIO_MIN_FONT = 18.sp
private val TRIO_MAX_FONT = 28.sp
private val PROGRESS_HEIGHT = 4.dp
