package dev.mockarr.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mockarr.app.R
import dev.mockarr.app.ui.theme.DialogAction
import dev.mockarr.app.ui.theme.MockarrDialog
import dev.mockarr.app.ui.theme.MockarrTheme
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.data.MockarrSettings

/**
 * Strava's settings icon grid, one tile per headline setting: icon, name, and
 * the current value as the subtitle. Tapping opens or toggles it.
 */
@Composable
fun SettingTile(
    iconRes: Int,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueTone: TileTone = TileTone.Neutral,
) {
    val valueColor = when (valueTone) {
        TileTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        TileTone.Ready -> MockarrTheme.colors.ready
        TileTone.Error -> MaterialTheme.colorScheme.error
    }
    Card(
        onClick = onClick,
        shape = Tokens.cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.semantics { contentDescription = "$title, $value" },
    ) {
        Column(modifier = Modifier.padding(Tokens.space4).heightIn(min = TILE_MIN_HEIGHT)) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(TILE_ICON),
            )
            Spacer(Modifier.height(Tokens.space3))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

enum class TileTone { Neutral, Ready, Error }

/** Small-caps section label, Strava's list grouping. */
@Composable
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Tokens.inset,
            end = Tokens.inset,
            top = Tokens.space6,
            bottom = Tokens.space1,
        ),
    )
}

/** A slider setting: icon, title + description, the value trailing, the slider underneath. */
@Composable
fun SliderRow(
    iconRes: Int,
    title: String,
    description: String,
    valueText: String,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    contentDescription: String,
) {
    Column(modifier = Modifier.padding(horizontal = Tokens.inset, vertical = Tokens.space2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onSliderChange,
            onValueChangeFinished = onSliderFinished,
            modifier = Modifier
                .padding(start = Tokens.space6 + Tokens.space4)
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription = valueText
                },
        )
    }
}

/** A navigating row with the current value trailing ("Default travel mode  Drive ›"). */
@Composable
fun ValueRow(iconRes: Int, title: String, description: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.space8 + Tokens.space6)
            .clickable(onClick = onClick, role = Role.Button)
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
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Tokens.space1))
        RowChevron()
    }
}

/** Custom OSRM server: URL field, a live Test, and a way back to the public server. */
@Composable
fun ServerDialog(
    currentUrl: String,
    isCustom: Boolean,
    managedAvailable: Boolean,
    publicOnly: Boolean,
    onPublicOnlyChange: (Boolean) -> Unit,
    testState: SettingsViewModel.TestState,
    onTest: (String) -> Unit,
    onSave: (String) -> Unit,
    onUsePublic: () -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(if (isCustom) currentUrl else "") }
    MockarrDialog(
        title = stringResource(R.string.settings_server_title),
        onDismissRequest = onDismiss,
        confirm = DialogAction(
            label = stringResource(R.string.dialog_save),
            onClick = { onSave(url) },
            enabled = url.isNotBlank(),
        ),
        dismiss = DialogAction(stringResource(R.string.dialog_cancel), onDismiss),
    ) {
        Text(
            text = stringResource(R.string.settings_server_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (managedAvailable && !isCustom) {
            // ADR 0003: Mockarr's managed routing/search vs. the public servers only.
            Spacer(Modifier.height(Tokens.space2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_server_managed_toggle),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_server_managed_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = !publicOnly, onCheckedChange = { onPublicOnlyChange(!it) })
            }
        }
        Spacer(Modifier.height(Tokens.space2))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = { Text(stringResource(R.string.settings_server_hint)) },
            singleLine = true,
            trailingIcon = {
                if (testState is SettingsViewModel.TestState.Testing) {
                    CircularProgressIndicator(modifier = Modifier.size(Tokens.space6), strokeWidth = 2.dp)
                } else {
                    TextButton(enabled = url.isNotBlank(), onClick = { onTest(url) }) {
                        Text(stringResource(R.string.settings_server_test))
                    }
                }
            },
        )
        when (testState) {
            SettingsViewModel.TestState.Success -> Text(
                text = stringResource(R.string.settings_server_ok),
                style = MaterialTheme.typography.bodySmall,
                color = MockarrTheme.colors.ready,
            )
            is SettingsViewModel.TestState.Failure -> Text(
                text = testState.message ?: stringResource(R.string.settings_server_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            else -> Unit
        }
        if (isCustom) {
            // Tertiary verb: stays a text button inside the body, not a third pill.
            TextButton(onClick = onUsePublic) { Text(stringResource(R.string.settings_server_use_public)) }
        }
    }
}

private val TILE_MIN_HEIGHT = 96.dp
private val TILE_ICON = 28.dp

/** A chevron trailing a navigating row, drawn (not typed) so it matches the icon set. */
@Composable
fun RowChevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Settings value for the routing backend: the user's URL, the public servers, or Mockarr's managed service. */
fun serverLabel(settings: MockarrSettings, managedAvailable: Boolean): Int = when {
    settings.customServerConfigured -> R.string.settings_server_custom
    settings.publicServersOnly || !managedAvailable -> R.string.settings_server_public
    else -> R.string.settings_server_managed
}
