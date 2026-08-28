package dev.mockarr.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mockarr.app.R
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.data.MockarrSettings
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

/**
 * The signature row under the stat card (DESIGN.md → The Action Row): mode
 * picker · Start · add/edit route, three equal slots, Strava's Record layout
 * translated to Material. One filled control per surface: Start.
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .padding(horizontal = Tokens.inset),
        verticalAlignment = Alignment.CenterVertically,
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
                Icon(painterResource(profile.iconRes()), contentDescription = null)
            }
        }
        val startLabel = stringResource(R.string.row_start)
        RowSlot(
            label = startLabel,
            modifier = Modifier.weight(1f),
            onClick = onStart.takeIf { canStart },
        ) {
            FilledIconButton(
                onClick = onStart,
                enabled = canStart,
                colors = IconButtonDefaults.filledIconButtonColors(),
                modifier = Modifier.size(START_BUTTON),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = stringResource(R.string.row_start_cd),
                    modifier = Modifier.size(START_ICON),
                )
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
                Icon(painterResource(R.drawable.ic_add_route), contentDescription = null)
            }
        }
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
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = modifier.then(clickModifier).padding(vertical = Tokens.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        control()
        Spacer(Modifier.height(Tokens.space1))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
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
                    .clickable(enabled = enabled) { onSelect(profile) }
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

/** One quick option: icon, title + description, trailing switch. Mirrors SettingsScreen's rows. */
@Composable
fun OptionSwitchRow(
    iconRes: Int,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.space8 + Tokens.space6)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = "$title. $description" }
            .padding(horizontal = Tokens.inset, vertical = Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Tokens.space4))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A navigating option row ("All settings ›"). */
@Composable
fun OptionLinkRow(iconRes: Int, title: String, onClick: () -> Unit, enabled: Boolean = true, divider: Boolean = true) {
    if (divider) HorizontalDivider(modifier = Modifier.padding(horizontal = Tokens.inset))
    val ink = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.touchTarget + Tokens.space2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Tokens.inset, vertical = Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Tokens.space4))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
        if (enabled) {
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Whether the options list offers Save for the loaded route. */
enum class SaveRowState { HIDDEN, UNSAVED, SAVED }

/** The drag-up list under the action row: Save, the settings that change a drive, then the other screens. */
@Composable
fun OptionsList(
    settings: MockarrSettings,
    saveState: SaveRowState,
    onSaveRoute: () -> Unit,
    followCamera: Boolean,
    onFollowChange: (Boolean) -> Unit,
    onStayChange: (Boolean) -> Unit,
    onTrafficChange: (Boolean) -> Unit,
    onWobbleChange: (Boolean) -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (saveState != SaveRowState.HIDDEN) {
        val saved = saveState == SaveRowState.SAVED
        OptionLinkRow(
            divider = false,
            iconRes = if (saved) R.drawable.ic_check else R.drawable.ic_route,
            title = stringResource(if (saved) R.string.option_saved else R.string.option_save_route),
            onClick = onSaveRoute,
            enabled = !saved,
        )
    }
    Text(
        text = stringResource(R.string.options_header).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space1),
    )
    OptionSwitchRow(
        iconRes = R.drawable.ic_target,
        title = stringResource(R.string.option_follow),
        description = stringResource(R.string.option_follow_desc),
        checked = followCamera,
        onCheckedChange = onFollowChange,
    )
    OptionSwitchRow(
        iconRes = R.drawable.ic_stop,
        title = stringResource(R.string.option_stay),
        description = stringResource(R.string.option_stay_desc),
        checked = settings.stayAtDestination,
        onCheckedChange = onStayChange,
    )
    OptionSwitchRow(
        iconRes = R.drawable.ic_schedule,
        title = stringResource(R.string.option_traffic),
        description = stringResource(R.string.option_traffic_desc),
        checked = settings.trafficSimEnabled,
        onCheckedChange = onTrafficChange,
    )
    OptionSwitchRow(
        iconRes = R.drawable.ic_route,
        title = stringResource(R.string.option_wobble),
        description = stringResource(R.string.option_wobble_desc),
        checked = settings.jitterEnabled,
        onCheckedChange = onWobbleChange,
    )
    OptionLinkRow(
        iconRes = R.drawable.ic_list,
        title = stringResource(R.string.option_routes),
        onClick = onOpenRoutes,
    )
    OptionLinkRow(
        iconRes = R.drawable.ic_settings,
        title = stringResource(R.string.option_settings),
        onClick = onOpenSettings,
    )
}

private val ROW_HEIGHT = 96.dp
private val SIDE_BUTTON = 48.dp
private val START_BUTTON = 64.dp
private val START_ICON = 32.dp
