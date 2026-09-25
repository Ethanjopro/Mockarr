package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mockarr.app.R
import dev.mockarr.app.ui.Motion
import dev.mockarr.app.ui.captionCase
import dev.mockarr.app.ui.rememberFormatter
import dev.mockarr.app.ui.theme.DialogAction
import dev.mockarr.app.ui.theme.MapIconPill
import dev.mockarr.app.ui.theme.MapPill
import dev.mockarr.app.ui.theme.MockarrDialog
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Pill
import dev.mockarr.app.ui.theme.SmallPill
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.Waypoint

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
                // The pill's height, so the row reads as one: ✕ · Done. Surface(onClick)
                // sets no role: announce it as the button it is.
                modifier = Modifier.size(Tokens.pillHeight).semantics { role = Role.Button },
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
fun DiscardRouteDialog(onDiscard: () -> Unit, onDismiss: () -> Unit, changesOnly: Boolean = false) {
    // ✕ undoes this editing session ("Discard changes"); the trash pill clears the route.
    val titleRes = if (changesOnly) R.string.builder_discard_changes_title else R.string.builder_discard_title
    val bodyRes = if (changesOnly) R.string.builder_discard_changes_body else R.string.builder_discard_body
    MockarrDialog(
        title = stringResource(titleRes),
        text = stringResource(bodyRes),
        onDismissRequest = onDismiss,
        confirm = if (changesOnly) {
            DialogAction(stringResource(R.string.builder_discard_changes), onDiscard, iconRes = R.drawable.ic_undo)
        } else {
            DialogAction(stringResource(R.string.builder_discard), onDiscard, iconRes = R.drawable.ic_delete)
        },
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

/** Expanded builder content: the stops (three rows tall, scrolling inside), then Save. */
@Composable
internal fun BuilderDetails(
    state: MapViewModel.UiState,
    selectedWaypoint: Int?,
    stayAtDestination: Boolean,
    listScrollEnabled: Boolean,
    onSelectWaypoint: (Int?) -> Unit,
    onSetWait: (Int) -> Unit,
    onClearWait: (Int) -> Unit,
    onRemoveStop: (Int) -> Unit,
    onMoveStop: (Int) -> Unit,
) {
    if (state.waypoints.isEmpty()) return
    val count = state.waypoints.size
    Text(
        text = captionCase(stringResource(R.string.sheet_stops_header, count)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space1).semantics { heading() },
    )
    // Long routes would bury the sheet: about three rows show and the rest scroll
    // inside — only once the sheet is expanded, so a drag up isn't spent on the list.
    // Longer lists end mid-row, fade at the bottom and count the hidden rows, so the
    // cut never reads as "that's all".
    val listState = rememberLazyListState()
    if (selectedWaypoint != null) {
        LaunchedEffect(selectedWaypoint) {
            // Marker taps can pick stop 6: bring its row in, but leave a visible row alone.
            val info = listState.layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { it.index == selectedWaypoint }
            val visible = row != null && row.offset >= 0 && row.offset + row.size <= info.viewportEndOffset
            if (!visible) listState.animateScrollToItem(selectedWaypoint)
        }
    }
    val rows = if (count > STOP_LIST_VISIBLE_ROWS) STOP_LIST_PEEK_ROWS else STOP_LIST_VISIBLE_ROWS.toFloat()
    val cap = Tokens.touchTarget * rows + if (selectedWaypoint != null) Tokens.touchTarget else 0.dp
    val hidden by remember(count) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastFull = info.visibleItemsInfo.lastOrNull { it.offset + it.size <= info.viewportEndOffset }
            if (lastFull == null) 0 else count - lastFull.index - 1
        }
    }
    LazyColumn(
        state = listState,
        userScrollEnabled = listScrollEnabled,
        modifier = Modifier.heightIn(max = cap).bottomFade(visible = hidden > 0, height = Tokens.touchTarget / 2),
    ) {
        itemsIndexed(state.waypoints) { index, waypoint ->
            StopRow(
                index = index,
                waypoint = waypoint,
                count = count,
                selected = index == selectedWaypoint,
                stayAtDestination = stayAtDestination,
                onClick = { onSelectWaypoint(if (index == selectedWaypoint) null else index) },
                onSetWait = { onSetWait(index) },
                onClearWait = { onClearWait(index) },
                onRemove = { onRemoveStop(index) },
                onMove = { onMoveStop(index) },
            )
        }
    }
}

/** One stop: numbered disc, role, wait, and its actions when selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopRow(
    index: Int,
    waypoint: Waypoint,
    count: Int,
    selected: Boolean,
    stayAtDestination: Boolean,
    onClick: () -> Unit,
    onSetWait: () -> Unit,
    onClearWait: () -> Unit,
    onRemove: () -> Unit,
    onMove: () -> Unit,
) {
    // Mirrors the map markers: the first stop is always the start.
    val isStart = index == 0
    val isEnd = index == count - 1 && count >= 2
    val background = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick, role = Role.Button)
            // The tint marks the stop whose actions are open; TalkBack hears it as selected.
            .semantics { this.selected = selected }
            .padding(horizontal = Tokens.inset),
    ) {
        // Fixed row heights keep "three rows" true for the list cap.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Tokens.touchTarget)) {
            StopDisc(number = index + 1, isStart = isStart, isEnd = isEnd)
            Spacer(Modifier.width(Tokens.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stopName(index, count, waypoint.name),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // A named stop still says which one it is (Start / Stop 2 / Destination), then its wait.
                val role = if (waypoint.name != null) stopName(index, count) else null
                val wait = if (waypoint.waitSeconds > 0) {
                    stringResource(R.string.sheet_waits, rememberFormatter().duration(waypoint.waitSeconds.toDouble()))
                } else {
                    null
                }
                if (role != null || wait != null) {
                    Row {
                        val secondary = MaterialTheme.typography.bodySmall
                        role?.let { Text(it, style = secondary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (role != null && wait != null) {
                            Text(
                                stringResource(R.string.separator),
                                style = secondary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        wait?.let { Text(it, style = secondary, color = MockarrTheme.colors.hold) }
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(
                        R.string.sheet_remove_stop_named,
                        stopName(index, count, waypoint.name),
                    ),
                )
            }
        }
        if (selected) {
            // On-sheet equivalent of the marker popover (TalkBack path). Wraps: three pills
            // outgrow the sheet at large font scales.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = Tokens.touchTarget),
            ) {
                if (stopStays(index, count, stayAtDestination)) {
                    // A fact, not a disabled button: 38 % ink failed contrast (critique, session 42).
                    Text(
                        text = stringResource(R.string.stop_menu_stays),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val waitLabel = if (waypoint.waitSeconds > 0) R.string.sheet_edit_wait else R.string.sheet_set_wait
                    SmallPill(stringResource(waitLabel), onSetWait)
                }
                if (waypoint.waitSeconds > 0) {
                    SmallPill(stringResource(R.string.sheet_remove_wait), onClearWait)
                }
                SmallPill(stringResource(R.string.stop_menu_move), onMove)
            }
        }
    }
}

private const val STOP_LIST_VISIBLE_ROWS = 3
private const val STOP_LIST_PEEK_ROWS = 3.5f
