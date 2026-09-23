package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mockarr.app.R
import dev.mockarr.app.ui.Motion.fadeThrough
import dev.mockarr.app.ui.theme.ActionPill
import dev.mockarr.app.ui.theme.OutlinedActionPill
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.RoutingProfile

/** Where a drive can begin when Start is pressed away from the route's first stop. */
enum class StartOrigin { HELD_SPOT, MY_LOCATION }

/** Start pressed with another origin available: the row splits into these two pills. */
data class StartChoice(
    val origin: StartOrigin,
    val onFromOrigin: () -> Unit,
    val onFromRouteStart: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The signature row under the stat card (DESIGN.md → The Action Row): mode
 * picker · Start · add/edit route, three equal slots, Strava's Record layout
 * translated to Material. One filled control per surface: Start. With a
 * [choice] pending, the row swaps (fade-through, like Pause → Resume/Finish)
 * to two pills — *From held spot* · *From route start* — and swaps back once
 * one is picked.
 */
@Composable
fun ActionRow(
    profile: RoutingProfile,
    canStart: Boolean,
    routeLoaded: Boolean,
    onPickMode: () -> Unit,
    onStart: () -> Unit,
    onEditRoute: () -> Unit,
    modifier: Modifier = Modifier,
    choice: StartChoice? = null,
    locating: Boolean = false,
    /** The loaded route was just driven to its end: Start reads "Drive again". */
    again: Boolean = false,
) {
    // Keyed on the origin, not the lambda-carrying object: a recomposition that
    // rebuilds the callbacks must not restart the enter transition (the pills
    // fading in again is what made the first tap on them land in nothing).
    // AnimatedContent's own SizeTransform animates the height; a second
    // animateContentSize on top only stretched the peek's re-anchor window.
    val latestChoice by rememberUpdatedState(choice)
    AnimatedContent(
        targetState = choice?.origin,
        transitionSpec = { fadeThrough() },
        label = "actionRow",
        modifier = modifier.fillMaxWidth(),
    ) { origin ->
        val pending = latestChoice?.takeIf { it.origin == origin }
        if (origin != null && pending != null) {
            StartChoiceRow(pending)
        } else {
            ActionSlots(profile, canStart, routeLoaded, onPickMode, onStart, onEditRoute, locating, again)
        }
    }
}

@Composable
private fun StartChoiceRow(choice: StartChoice) {
    // Natural height, not the three-slot row's 120dp box: a caption and one
    // pill row, ending space3 above the inset like every other peek (session 31).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.inset)
            .padding(bottom = Tokens.space3),
    ) {
        // Say what the two pills are for (Ethan): a centred section-header caption,
        // a heading to TalkBack, and a visible way out beside it (Back and a map tap
        // cancel too, but nothing said so).
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.row_start_choice_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).semantics { heading() },
            )
            TextButton(onClick = choice.onCancel, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartChoicePills(choice)
        }
        // The consequence, once: one choice drives there, the other jumps — and a jump
        // is exactly what other apps would see.
        Text(
            text = stringResource(
                when (choice.origin) {
                    StartOrigin.HELD_SPOT -> R.string.row_start_choice_help_hold
                    StartOrigin.MY_LOCATION -> R.string.row_start_choice_help_me
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Tokens.space2),
        )
    }
}

@Composable
private fun RowScope.StartChoicePills(choice: StartChoice) {
    // Equal choices, one system: the alternative outlined on the left, the
    // route's own start filled on the right (no amber / pale / black fills).
    when (choice.origin) {
        StartOrigin.HELD_SPOT -> OutlinedActionPill(
            label = stringResource(R.string.row_start_from_hold),
            iconRes = R.drawable.ic_stat_pin,
            enabled = true,
            onClick = choice.onFromOrigin,
            contentDescription = stringResource(R.string.row_start_from_hold_cd),
        )
        StartOrigin.MY_LOCATION -> OutlinedActionPill(
            label = stringResource(R.string.row_start_from_me),
            iconRes = R.drawable.ic_target,
            enabled = true,
            onClick = choice.onFromOrigin,
            contentDescription = stringResource(R.string.row_start_from_me_cd),
        )
    }
    ActionPill(
        label = stringResource(R.string.row_start_from_route),
        iconRes = R.drawable.ic_play,
        enabled = true,
        onClick = choice.onFromRouteStart,
        contentDescription = stringResource(R.string.row_start_from_route_cd),
    )
}

@Composable
private fun ActionSlots(
    profile: RoutingProfile,
    canStart: Boolean,
    routeLoaded: Boolean,
    onPickMode: () -> Unit,
    onStart: () -> Unit,
    onEditRoute: () -> Unit,
    locating: Boolean,
    again: Boolean,
) {
    // Circles share a top edge (Strava): the row reads higher and each label
    // sits under its own circle.
    // The trio is one centred cluster, not three columns spread edge to edge.
    Row(
        modifier = Modifier
            .widthIn(max = ROW_MAX_WIDTH)
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .padding(horizontal = Tokens.inset),
        verticalAlignment = Alignment.Top,
    ) {
        val modeLabel = stringResource(profile.shortLabelRes())
        val modeDescription = stringResource(R.string.row_mode_cd, modeLabel)
        RowSlot(
            label = modeLabel,
            description = modeDescription,
            modifier = Modifier.weight(1f),
            onClick = onPickMode,
        ) {
            FilledTonalIconButton(
                onClick = onPickMode,
                modifier = Modifier.size(SIDE_BUTTON).clearAndSetSemantics {},
            ) {
                Icon(painterResource(profile.iconRes()), contentDescription = null, modifier = Modifier.size(SIDE_ICON))
            }
        }
        val startLabel = stringResource(if (again) profile.againLabelRes() else R.string.row_start)
        // The label lives on the slot, so the locating spinner doesn't leave it unnamed,
        // and a disabled Start is announced as disabled instead of vanishing.
        val startDescription = stringResource(if (again) R.string.row_start_again_cd else R.string.row_start_cd)
        RowSlot(
            label = startLabel,
            description = startDescription,
            modifier = Modifier.weight(1f),
            enabled = canStart && !locating,
            onClick = onStart,
        ) {
            FilledIconButton(
                onClick = onStart,
                enabled = canStart && !locating,
                modifier = Modifier.size(START_BUTTON).clearAndSetSemantics {},
            ) {
                // While the real location resolves, the button says so instead of play.
                if (locating) {
                    CircularProgressIndicator(modifier = Modifier.size(SIDE_ICON), strokeWidth = SPINNER_STROKE)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        modifier = Modifier.size(START_ICON),
                    )
                }
            }
        }
        val routeLabel = stringResource(if (routeLoaded) R.string.row_switch_route else R.string.row_add_route)
        RowSlot(label = routeLabel, description = routeLabel, modifier = Modifier.weight(1f), onClick = onEditRoute) {
            FilledTonalIconButton(
                onClick = onEditRoute,
                modifier = Modifier.size(SIDE_BUTTON).clearAndSetSemantics {},
            ) {
                Icon(
                    painterResource(R.drawable.ic_add_route),
                    contentDescription = null,
                    modifier = Modifier.size(SIDE_ICON),
                )
            }
        }
    }
}

/**
 * One action-row slot: circle plus label, one tap target and ONE TalkBack node
 * (the circle's own semantics are cleared by the caller) — two focus stops per
 * slot read "Drive, button · Travel mode: Drive, button" (audit, session 41).
 */
@Composable
private fun RowSlot(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    control: @Composable () -> Unit,
) {
    // The label is part of the target (Strava taps the whole slot).
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description }
            .padding(vertical = Tokens.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        control()
        Spacer(Modifier.height(Tokens.space1))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Strava's sport picker, reduced to the three routing profiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModePickerSheet(
    selected: RoutingProfile,
    profilesUnlocked: Boolean,
    onSelect: (RoutingProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.mode_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space2).semantics { heading() },
        )
        // One radio group, so TalkBack says "1 of 3" and the selection.
        Column(modifier = Modifier.selectableGroup()) {
            RoutingProfile.entries.forEach { profile ->
                // The public routing server only serves driving.
                val enabled = profile == RoutingProfile.DRIVING || profilesUnlocked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Tokens.space8 + Tokens.space4)
                        .selectable(
                            selected = profile == selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(profile) },
                        )
                        .padding(horizontal = Tokens.inset, vertical = Tokens.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    Icon(painterResource(profile.iconRes()), contentDescription = null, tint = tint)
                    Spacer(Modifier.width(Tokens.space4))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(profile.shortLabelRes()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = tint,
                        )
                        if (!enabled) {
                            Text(
                                text = stringResource(R.string.mode_locked),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (profile == selected) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Tokens.space6))
    }
}

private val ROW_HEIGHT = 120.dp
private val ROW_MAX_WIDTH = 320.dp
private val SPINNER_STROKE = 3.dp

// Strava's Record row (hud-048), measured: side circles ≈ 58pt with ≈ 28pt
// glyphs, Start ≈ 68pt, 15pt labels. Ours run a step larger — the M3 24dp
// icons and 12sp labels read small even at matching circle sizes (session 19).
private val SIDE_BUTTON = 64.dp
private val SIDE_ICON = 28.dp
private val START_BUTTON = 80.dp
private val START_ICON = 40.dp
