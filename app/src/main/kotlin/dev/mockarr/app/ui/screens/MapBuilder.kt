package dev.mockarr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import dev.mockarr.app.R
import dev.mockarr.app.ui.formatDistance
import dev.mockarr.app.ui.formatDurationShort
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.DistanceUnits
import kotlin.math.roundToInt

/**
 * Builder-mode peek: the route-under-construction trio (Distance · Duration ·
 * Stops), the illustrated empty hint before the first stop, and the Done / ✕
 * row that returns to the Record layout. Strava's route builder, in place.
 */
@Composable
fun BuilderPeek(
    state: MapViewModel.UiState,
    units: DistanceUnits,
    onSave: () -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    val route = state.route
    Column(modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space3)) {
        if (state.waypoints.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.space2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_route),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Tokens.space8 + Tokens.space2),
                )
                Spacer(Modifier.height(Tokens.space2))
                Text(
                    text = stringResource(R.string.sheet_idle_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.sheet_idle_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val seconds = route?.let { it.durationSeconds * state.trafficFactor + it.waypointWaitsSeconds.sum() }
            val duration = seconds?.let {
                formatDurationShort(
                    (it / SECONDS_PER_MINUTE).roundToInt().coerceAtLeast(1) * SECONDS_PER_MINUTE.toDouble(),
                )
            }
            val placeholder = stringResource(R.string.stat_placeholder)
            StatTrio(
                cells = listOf(
                    StatCell(
                        stringResource(R.string.stat_distance),
                        route?.let { formatDistance(it.distanceMeters, units) } ?: placeholder,
                    ),
                    StatCell(stringResource(R.string.stat_duration), duration ?: placeholder),
                    StatCell(stringResource(R.string.stat_stops), state.waypoints.size.toString()),
                ),
            )
        }
        Spacer(Modifier.height(Tokens.space3))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
        ) {
            FilledTonalIconButton(onClick = onClose) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.builder_close_cd),
                )
            }
            // Strava keeps Save in the builder sheet (map-348): outlined beside Done.
            OutlinedButton(onClick = onSave, enabled = route != null && !state.routeIsFallback) {
                Text(stringResource(R.string.builder_save))
            }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                Spacer(Modifier.width(Tokens.space2))
                Text(stringResource(R.string.builder_done))
            }
        }
    }
}

/** Floating tool pills on the map's right edge while building: undo · reverse · more. */
@Composable
fun BuilderTools(
    canUndo: Boolean,
    canReverse: Boolean,
    onUndo: () -> Unit,
    onReverse: () -> Unit,
    onAddAtCenter: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Tokens.space2)) {
        FilledTonalIconButton(onClick = onUndo, enabled = canUndo) {
            Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.builder_undo_cd))
        }
        FilledTonalIconButton(onClick = onReverse, enabled = canReverse) {
            Icon(painterResource(R.drawable.ic_swap), contentDescription = stringResource(R.string.builder_reverse_cd))
        }
        Column {
            FilledTonalIconButton(onClick = { menuOpen = true }) {
                Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.builder_more_cd))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.builder_add_at_center)) },
                    onClick = {
                        menuOpen = false
                        onAddAtCenter()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.builder_clear_all)) },
                    enabled = canUndo,
                    onClick = {
                        menuOpen = false
                        onClearAll()
                    },
                )
            }
        }
    }
}

/** Leaving the builder with unsaved stops. */
@Composable
fun DiscardRouteDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.builder_discard_title)) },
        text = { Text(stringResource(R.string.builder_discard_body)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.builder_discard)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.builder_keep)) } },
    )
}

private const val SECONDS_PER_MINUTE = 60
