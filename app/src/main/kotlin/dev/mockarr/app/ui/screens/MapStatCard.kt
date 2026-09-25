package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mockarr.app.R
import dev.mockarr.app.ui.Motion
import dev.mockarr.app.ui.rememberFormatter
import dev.mockarr.app.ui.theme.MapPopover
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.DistanceUnits
import dev.mockarr.core.model.Route

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
    /** TalkBack-only actions on the in-drive stats (set a coming stop's wait). */
    statsActions: List<CustomAccessibilityAction> = emptyList(),
    /** TalkBack-only actions on the band (finish a pending Move at the map centre). */
    stripActions: List<CustomAccessibilityAction> = emptyList(),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Tokens.cardShape,
        color = MockarrTheme.colors.floating,
        border = MockarrTheme.colors.floatingBorder(),
        shadowElevation = Tokens.cardElevation,
    ) {
        // Leaving, the trio keeps its last numbers while it folds away: it used to redraw
        // empty, and a blank white block shrank out of the card.
        var lastStats by remember { mutableStateOf(stats) }
        SideEffect { if (stats != null) lastStats = stats }
        Column {
            StatusStrip(
                text = strip.text,
                tone = strip.tone,
                actionLabel = strip.actionLabel,
                onAction = onStripAction,
                trailing = trailing,
                spoken = strip.spoken,
                customActions = stripActions,
            )
            AnimatedVisibility(
                visible = stats != null,
                enter = expandVertically(tween(Motion.STANDARD_MILLIS)) + fadeIn(tween(Motion.STANDARD_MILLIS)),
                exit = shrinkVertically(tween(Motion.STANDARD_MILLIS)),
            ) {
                // Only the stats block is tappable — the strip keeps its own action.
                val editLabel = stringResource(R.string.stat_trio_edit_cd)
                // onClickLabel, not a content description: TalkBack reads the numbers,
                // then "double-tap to Edit the route" (a description would replace them).
                val clickable = if (onStatsClick != null) {
                    Modifier.clickable(onClick = onStatsClick, onClickLabel = editLabel, role = Role.Button)
                } else {
                    Modifier.semantics { if (statsActions.isNotEmpty()) customActions = statsActions }
                }
                // Its own size animation: a planned trio becoming the drive's (with its progress
                // bar) grows smoothly. The whole card used to animate too, and the two fought.
                Column(
                    modifier = clickable
                        .animateContentSize()
                        .padding(horizontal = Tokens.inset, vertical = Tokens.space3),
                ) {
                    (stats ?: lastStats)?.invoke()
                }
            }
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

/**
 * One value-over-label cell of the stat trio. Only a cell that [rolls] animates
 * its value like a speedometer wheel (the drive's speed); [magnitude] is the
 * number behind [value] in any unit, so a change knows whether to roll up or down.
 */
data class StatCell(val label: String, val value: String, val magnitude: Double? = null, val rolls: Boolean = false)

/** Three equal cells: bold tabular value with its label under it (Strava's trio); no hero numeral. */
@Composable
fun StatTrio(cells: List<StatCell>, modifier: Modifier = Modifier, mergeCells: Boolean = true) {
    Row(modifier = modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            // Each cell reads as one "12 min, Time left" — unless a tappable parent
            // (the loaded route's edit entry) merges the whole trio into one node.
            val cellSemantics = if (mergeCells) Modifier.semantics(mergeDescendants = true) {} else Modifier
            Column(
                modifier = Modifier.weight(1f).then(cellSemantics),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Tabular figures keep every digit the same width so nothing shifts;
                // "1 h 12 min" overflows a third of the card at 28sp: shrink, never clip.
                val style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_FIGURES,
                )
                if (cell.rolls) {
                    // The speedometer: the value rolls over like an instrument wheel.
                    RollingText(
                        text = cell.value,
                        magnitude = cell.magnitude,
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface,
                        minFontSize = TRIO_MIN_FONT,
                        maxFontSize = TRIO_MAX_FONT,
                    )
                } else {
                    Text(
                        text = cell.value,
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(TRIO_MIN_FONT, TRIO_MAX_FONT, TRIO_FONT_STEP),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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

/**
 * Progress under the trio: thin, accent, no label — the trio already says the
 * numbers. [stops] are the intermediate stops as fractions of the route, drawn
 * as ticks so the bar and the notification's Live Update read as one system.
 */
@Composable
fun DriveProgress(progress: Float, modifier: Modifier = Modifier, stops: List<Float> = emptyList()) {
    val passed = MaterialTheme.colorScheme.onPrimary
    val ahead = MaterialTheme.colorScheme.outline
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
            .fillMaxWidth()
            .height(PROGRESS_HEIGHT)
            .drawWithContent {
                drawContent()
                val radius = size.height / 2
                stops.forEach { fraction ->
                    val colour = if (fraction <= progress) passed else ahead
                    drawCircle(colour, radius, Offset(size.width * fraction, radius))
                }
            },
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        drawStopIndicator = {},
    )
}

/** Intermediate stops as fractions of the route's length (leg boundaries), or empty without legs. */
internal fun stopFractions(route: Route?): List<Float> {
    val legs = route?.legs ?: return emptyList()
    val lengths = legs.map { it.segmentDistancesMeters.sum() }
    val total = lengths.sum()
    if (total <= 0.0 || legs.size < 2) return emptyList()
    return lengths.runningReduce(Double::plus).dropLast(1).map { (it / total).toFloat() }
}

/**
 * The loaded route's Distance · Duration · Stops, or null when nothing is loaded. The
 * duration is [plannedSeconds], the drive's own estimate — the Time left it opens on.
 */
@Composable
internal fun recordCells(state: MapViewModel.UiState, plannedSeconds: Double?, units: DistanceUnits): List<StatCell>? {
    val route = state.route ?: return null
    val formatter = rememberFormatter()
    val placeholder = stringResource(R.string.stat_placeholder)
    return listOf(
        StatCell(stringResource(R.string.stat_distance), formatter.distance(route.distanceMeters, units)),
        StatCell(stringResource(R.string.stat_duration), plannedSeconds?.let(formatter::duration) ?: placeholder),
        StatCell(stringResource(R.string.stat_stops), state.waypoints.size.toString()),
    )
}

private const val TABULAR_FIGURES = "tnum"
private val TRIO_MIN_FONT = 18.sp
private val TRIO_MAX_FONT = 28.sp

// RollingText fits in the same 2 sp steps, so a rolling and a still value settle at one size.
private val TRIO_FONT_STEP = 2.sp
private val PROGRESS_HEIGHT = 4.dp
