package dev.mockarr.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
import dev.mockarr.app.ui.Motion.fadeThrough
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.RoutingProfile

/** Icon + short label for a travel mode, shared by the row and the picker. */
internal fun RoutingProfile.iconRes(): Int = when (this) {
    RoutingProfile.DRIVING -> R.drawable.ic_car
    RoutingProfile.WALKING -> R.drawable.ic_walk
    RoutingProfile.CYCLING -> R.drawable.ic_bike
}

internal fun RoutingProfile.shortLabelRes(): Int = when (this) {
    RoutingProfile.DRIVING -> R.string.row_mode_drive
    RoutingProfile.WALKING -> R.string.row_mode_walk
    RoutingProfile.CYCLING -> R.string.row_mode_cycle
}

/** Where a drive can begin when Start is pressed away from the route's first stop. */
enum class StartOrigin { HELD_SPOT, MY_LOCATION }

/** Start pressed with another origin available: the row splits into these two pills. */
data class StartChoice(
    val origin: StartOrigin,
    val onFromOrigin: () -> Unit,
    val onFromRouteStart: () -> Unit,
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
) {
    AnimatedContent(
        targetState = choice,
        transitionSpec = { fadeThrough() },
        label = "actionRow",
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) { pending ->
        if (pending != null) {
            StartChoiceRow(pending)
        } else {
            ActionSlots(profile, canStart, routeLoaded, onPickMode, onStart, onEditRoute, locating)
        }
    }
}

@Composable
private fun StartChoiceRow(choice: StartChoice) {
    val description = stringResource(R.string.row_start_choice_cd)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .padding(horizontal = Tokens.inset)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.Center,
    ) {
        // Say what the two pills are for (Ethan): a centred section-header caption.
        Text(
            text = stringResource(R.string.row_start_choice_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = Tokens.space2),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartChoicePills(choice)
        }
    }
}

@Composable
private fun RowScope.StartChoicePills(choice: StartChoice) {
    when (choice.origin) {
        // Amber Hold: the pill wears the held spot's colour, like the pin and the strip.
        StartOrigin.HELD_SPOT -> ActionPill(
            label = stringResource(R.string.row_start_from_hold),
            iconRes = R.drawable.ic_stat_pin,
            enabled = true,
            onClick = choice.onFromOrigin,
            contentDescription = stringResource(R.string.row_start_from_hold_cd),
            colors = ButtonDefaults.buttonColors(
                containerColor = MockarrTheme.colors.holdContainer,
                contentColor = MockarrTheme.colors.onHoldContainer,
            ),
        )
        // Primary, not amber: locating is where the device really is, no hold involved.
        StartOrigin.MY_LOCATION -> ActionPill(
            label = stringResource(R.string.row_start_from_me),
            iconRes = R.drawable.ic_target,
            enabled = true,
            onClick = choice.onFromOrigin,
            contentDescription = stringResource(R.string.row_start_from_me_cd),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
    }
    ActionPill(
        label = stringResource(R.string.row_start_from_route),
        iconRes = R.drawable.ic_play,
        enabled = true,
        onClick = choice.onFromRouteStart,
        contentDescription = stringResource(R.string.row_start_from_route_cd),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
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
        RowSlot(label = modeLabel, modifier = Modifier.weight(1f), onClick = onPickMode) {
            FilledTonalIconButton(
                onClick = onPickMode,
                modifier = Modifier
                    .size(SIDE_BUTTON)
                    .semantics { contentDescription = modeDescription },
            ) {
                Icon(painterResource(profile.iconRes()), contentDescription = null, modifier = Modifier.size(SIDE_ICON))
            }
        }
        val startLabel = stringResource(R.string.row_start)
        RowSlot(
            label = startLabel,
            modifier = Modifier.weight(1f),
            onClick = onStart.takeIf { canStart && !locating },
        ) {
            FilledIconButton(
                onClick = onStart,
                enabled = canStart && !locating,
                colors = IconButtonDefaults.filledIconButtonColors(),
                modifier = Modifier.size(START_BUTTON),
            ) {
                // While the real location resolves, the button says so instead of play.
                if (locating) {
                    CircularProgressIndicator(modifier = Modifier.size(SIDE_ICON), strokeWidth = SPINNER_STROKE)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = stringResource(R.string.row_start_cd),
                        modifier = Modifier.size(START_ICON),
                    )
                }
            }
        }
        val routeLabel = stringResource(if (routeLoaded) R.string.row_switch_route else R.string.row_add_route)
        RowSlot(label = routeLabel, modifier = Modifier.weight(1f), onClick = onEditRoute) {
            FilledTonalIconButton(
                onClick = onEditRoute,
                modifier = Modifier
                    .size(SIDE_BUTTON)
                    .semantics { contentDescription = routeLabel },
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

/** A 56dp pill filling its share of a row: Pause / Resume / Finish and the Start choice. */
@Composable
internal fun RowScope.ActionPill(
    label: String,
    iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentDescription: String? = null,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = Modifier.weight(1f).height(Tokens.pillHeight).then(semantics),
    ) {
        Icon(painterResource(iconRes), contentDescription = null)
        Spacer(Modifier.width(Tokens.space2))
        // One line always: at large font scales the label shrinks rather than wrapping.
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = PILL_MIN_FONT, maxFontSize = PILL_MAX_FONT),
        )
    }
}

@Composable
private fun RowSlot(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    // The label is part of the target (Strava taps the whole slot).
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier
    Column(
        modifier = modifier.then(clickModifier).padding(vertical = Tokens.space1),
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
    customServerConfigured: Boolean,
    onSelect: (RoutingProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.mode_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space2),
        )
        RoutingProfile.entries.forEach { profile ->
            // The public routing server only serves driving.
            val enabled = profile == RoutingProfile.DRIVING || customServerConfigured
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.space8 + Tokens.space4)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(profile) }
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
        Spacer(Modifier.height(Tokens.space6))
    }
}

private val ROW_HEIGHT = 120.dp
private val ROW_MAX_WIDTH = 320.dp
private val SPINNER_STROKE = 3.dp
private val PILL_MIN_FONT = 12.sp
private val PILL_MAX_FONT = 16.sp

// Strava's Record row (hud-048), measured: side circles ≈ 58pt with ≈ 28pt
// glyphs, Start ≈ 68pt, 15pt labels. Ours run a step larger — the M3 24dp
// icons and 12sp labels read small even at matching circle sizes (session 19).
private val SIDE_BUTTON = 64.dp
private val SIDE_ICON = 28.dp
private val START_BUTTON = 80.dp
private val START_ICON = 40.dp
