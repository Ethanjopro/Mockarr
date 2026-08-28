package dev.mockarr.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
fun OptionLinkRow(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    divider: Boolean = true,
    busy: Boolean = false,
) {
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
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(SPINNER_SIZE), strokeWidth = 2.dp)
        } else {
            Icon(painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    saving: Boolean,
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
            enabled = !saved && !saving,
            busy = saving,
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

private val SPINNER_SIZE = 20.dp
