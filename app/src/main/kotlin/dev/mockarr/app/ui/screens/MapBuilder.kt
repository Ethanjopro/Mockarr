package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mockarr.app.R
import dev.mockarr.app.ui.Motion
import dev.mockarr.app.ui.theme.DialogAction
import dev.mockarr.app.ui.theme.MapIconPill
import dev.mockarr.app.ui.theme.MapPill
import dev.mockarr.app.ui.theme.MockarrDialog
import dev.mockarr.app.ui.theme.Pill
import dev.mockarr.app.ui.theme.Tokens

/**
 * Builder-mode peek: the illustrated hint until the route exists (no stops:
 * how to start; one stop: how to finish), then the Done / ✕ row that returns
 * to the Record layout. The trio lives on the stat card above, as in every
 * other state. Strava's route builder, in place. Save lives with the map
 * tools ([BuilderTools]), not here.
 */
@Composable
fun BuilderPeek(
    state: MapViewModel.UiState,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space3)) {
        when (state.waypoints.size) {
            0 -> BuilderHint(R.string.sheet_idle_title, R.string.sheet_idle_body)
            1 -> BuilderHint(R.string.sheet_one_stop_title, R.string.sheet_one_stop_body)
            else -> Unit
        }
        // The gap belongs to the hint: with two stops the row sits right under the handle (peek rhythm).
        if (state.waypoints.size < 2) Spacer(Modifier.height(Tokens.space3))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
        ) {
            // Strava's ✕: a white circle; inside the sheet it needs a hairline, not a shadow.
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                // The pill's height, so the row reads as one: ✕ · Done.
                modifier = Modifier.size(Tokens.pillHeight),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.builder_close_cd),
                    )
                }
            }
            Pill(
                label = stringResource(R.string.builder_done),
                onClick = onDone,
                iconRes = R.drawable.ic_check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The builder's empty state: one glyph, a title, one line of how. */
@Composable
private fun BuilderHint(titleRes: Int, bodyRes: Int) {
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
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Strava's builder tools, bottom-centre of the map, revealed as they start to
 * mean something (distill, session 41): Undo / Redo once there is history to
 * step through, and clear · save · reverse once there are two stops to act on —
 * a first search pick used to land on five tools at once, most of them inert.
 * Clear asks first (the host shows the dialog); Save waits for the route's
 * place name, and the spinner says so.
 */
@Composable
fun BuilderTools(
    stopCount: Int,
    canSave: Boolean,
    saving: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReverse: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routeTools = stopCount >= 2
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Tokens.space3)) {
        AnimatedVisibility(visible = routeTools, enter = Motion.floatingEnter, exit = Motion.floatingExit) {
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.space3)) {
                MapIconPill(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.builder_clear_all),
                    onClick = onClearAll,
                )
                MapPill(
                    onClick = onSave,
                    contentDescription = stringResource(R.string.builder_save_cd),
                    // Visible but disabled for a straight-line fallback: it can't be saved yet.
                    enabled = canSave && !saving,
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Tokens.spinnerSize),
                            strokeWidth = Tokens.spinnerStroke,
                        )
                    } else {
                        Icon(painterResource(R.drawable.ic_save), contentDescription = null)
                    }
                }
                MapIconPill(
                    painter = painterResource(R.drawable.ic_swap),
                    contentDescription = stringResource(R.string.builder_reverse_cd),
                    onClick = onReverse,
                )
            }
        }
        AnimatedVisibility(visible = canUndo, enter = Motion.floatingEnter, exit = Motion.floatingExit) {
            MapIconPill(
                painter = painterResource(R.drawable.ic_undo),
                contentDescription = stringResource(R.string.builder_undo_cd),
                onClick = onUndo,
            )
        }
        AnimatedVisibility(visible = canRedo, enter = Motion.floatingEnter, exit = Motion.floatingExit) {
            MapIconPill(
                painter = painterResource(R.drawable.ic_redo),
                contentDescription = stringResource(R.string.builder_redo_cd),
                onClick = onRedo,
            )
        }
    }
}

/** Clearing the stops (the trash pill, or ✕ with unsaved stops): says what it loses, never red. */
@Composable
fun DiscardRouteDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    MockarrDialog(
        title = stringResource(R.string.builder_discard_title),
        text = stringResource(R.string.builder_discard_body),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(R.string.builder_discard), onDiscard, iconRes = R.drawable.ic_delete),
        dismiss = DialogAction(stringResource(R.string.dialog_cancel), onDismiss),
    )
}

/**
 * Fades the bottom [height] of the content to transparent while [visible] — the
 * "there is more below" cue for a capped list. An alpha mask (DstIn), so it needs
 * no background colour and works over any sheet surface.
 */
fun Modifier.bottomFade(visible: Boolean, height: Dp): Modifier {
    if (!visible) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - height.toPx(),
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}
